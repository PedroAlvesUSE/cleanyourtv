package com.gathin.tvhome;

import java.nio.charset.StandardCharsets;

/**
 * Gerador de QR code mínimo: modo byte, correção de erro L, versões 1 a 5 (até 106 bytes), um só bloco.
 * Basta para uma URL da rede local. Devolve a matriz de módulos (true = escuro) sem a zona silenciosa.
 */
final class Qr {
    private static final int[] DATA_WORDS = {0, 19, 34, 55, 80, 108};
    private static final int[] EC_WORDS = {0, 7, 10, 15, 20, 26};
    static final int MAX_BYTES = 106;

    private Qr() { }

    /** Capacidade em bytes da versão mais baixa que serve, ou 0 se o texto não cabe. */
    static int version(int length) {
        for (int v = 1; v <= 5; v++) if (length <= DATA_WORDS[v] - 2) return v;
        return 0;
    }

    /** A matriz do QR, ou null se o texto passa de 106 bytes. */
    static boolean[][] encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int version = version(bytes.length);
        if (version == 0) return null;
        byte[] words = codewords(bytes, version);
        int size = 17 + 4 * version;
        boolean[][] fn = new boolean[size][size];
        boolean[][] base = new boolean[size][size];
        drawFunctions(base, fn, version, size);
        placeData(base, fn, words, size);
        int bestMask = 0;
        long bestScore = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            boolean[][] t = copy(base);
            applyMask(t, fn, m, size);
            drawFormat(t, m, size);
            long score = penalty(t, size);
            if (score < bestScore) {
                bestScore = score;
                bestMask = m;
            }
        }
        applyMask(base, fn, bestMask, size);
        drawFormat(base, bestMask, size);
        return base;
    }

    /** Dados mais correção de erro, prontos para colocar na matriz. */
    static byte[] codewords(byte[] data, int version) {
        int capacity = DATA_WORDS[version];
        int ec = EC_WORDS[version];
        StringBuilder bits = new StringBuilder();
        append(bits, 4, 4);
        append(bits, data.length, 8);
        for (byte b : data) append(bits, b & 0xFF, 8);
        append(bits, 0, Math.min(4, capacity * 8 - bits.length()));
        while (bits.length() % 8 != 0) bits.append('0');
        byte[] out = new byte[capacity + ec];
        for (int i = 0; i < bits.length() / 8; i++) out[i] = (byte) Integer.parseInt(bits.substring(8 * i, 8 * i + 8), 2);
        for (int i = bits.length() / 8, pad = 0xEC; i < capacity; i++, pad ^= 0xEC ^ 0x11) out[i] = (byte) pad;
        byte[] rem = reedSolomon(java.util.Arrays.copyOf(out, capacity), ec);
        System.arraycopy(rem, 0, out, capacity, ec);
        return out;
    }

    private static void append(StringBuilder sb, int value, int count) {
        for (int i = count - 1; i >= 0; i--) sb.append((value >>> i) & 1);
    }

    private static int mul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    static byte[] reedSolomon(byte[] data, int degree) {
        int[] gen = new int[degree];
        gen[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                gen[j] = mul(gen[j], root);
                if (j + 1 < degree) gen[j] ^= gen[j + 1];
            }
            root = mul(root, 2);
        }
        byte[] result = new byte[degree];
        for (byte b : data) {
            int factor = (b & 0xFF) ^ (result[0] & 0xFF);
            System.arraycopy(result, 1, result, 0, degree - 1);
            result[degree - 1] = 0;
            for (int i = 0; i < degree; i++) result[i] ^= mul(gen[i], factor);
        }
        return result;
    }

    private static void set(boolean[][] m, boolean[][] fn, int x, int y, boolean dark) {
        m[y][x] = dark;
        fn[y][x] = true;
    }

    private static void drawFunctions(boolean[][] m, boolean[][] fn, int version, int size) {
        for (int i = 0; i < size; i++) {
            set(m, fn, 6, i, i % 2 == 0);
            set(m, fn, i, 6, i % 2 == 0);
        }
        finder(m, fn, 3, 3, size);
        finder(m, fn, size - 4, 3, size);
        finder(m, fn, 3, size - 4, size);
        if (version >= 2) {
            int c = size - 7;
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    set(m, fn, c + dx, c + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                }
            }
        }
        // Reserva as áreas do formato; drawFormat escreve os bits depois.
        for (int i = 0; i < 9; i++) {
            fn[8][i] = true;
            fn[i][8] = true;
        }
        for (int i = 0; i < 8; i++) {
            fn[8][size - 1 - i] = true;
            fn[size - 1 - i][8] = true;
        }
        set(m, fn, 8, size - 8, true);
    }

    private static void finder(boolean[][] m, boolean[][] fn, int cx, int cy, int size) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (x < 0 || y < 0 || x >= size || y >= size) continue;
                int d = Math.max(Math.abs(dx), Math.abs(dy));
                set(m, fn, x, y, d != 2 && d != 4);
            }
        }
    }

    private static void placeData(boolean[][] m, boolean[][] fn, byte[] data, int size) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!fn[y][x] && i < data.length * 8) {
                        m[y][x] = ((data[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
                        i++;
                    }
                }
            }
        }
    }

    private static boolean masked(int mask, int x, int y) {
        switch (mask) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return (x / 3 + y / 2) % 2 == 0;
            case 5: return x * y % 2 + x * y % 3 == 0;
            case 6: return (x * y % 2 + x * y % 3) % 2 == 0;
            default: return ((x + y) % 2 + x * y % 3) % 2 == 0;
        }
    }

    private static void applyMask(boolean[][] m, boolean[][] fn, int mask, int size) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) if (!fn[y][x] && masked(mask, x, y)) m[y][x] = !m[y][x];
        }
    }

    /** Os 15 bits de formato para ECC L (01) e a máscara dada: BCH(15,5) xor 0x5412. */
    static int formatBits(int mask) {
        int data = (1 << 3) | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        return ((data << 10) | rem) ^ 0x5412;
    }

    private static void drawFormat(boolean[][] m, int mask, int size) {
        int bits = formatBits(mask);
        for (int i = 0; i <= 5; i++) m[i][8] = bit(bits, i);
        m[7][8] = bit(bits, 6);
        m[8][8] = bit(bits, 7);
        m[8][7] = bit(bits, 8);
        for (int i = 9; i < 15; i++) m[8][14 - i] = bit(bits, i);
        for (int i = 0; i < 8; i++) m[8][size - 1 - i] = bit(bits, i);
        for (int i = 8; i < 15; i++) m[size - 15 + i][8] = bit(bits, i);
        m[size - 8][8] = true;
    }

    private static boolean bit(int v, int i) {
        return ((v >>> i) & 1) != 0;
    }

    private static boolean[][] copy(boolean[][] m) {
        boolean[][] c = new boolean[m.length][];
        for (int i = 0; i < m.length; i++) c[i] = m[i].clone();
        return c;
    }

    private static long penalty(boolean[][] m, int size) {
        long score = 0;
        int dark = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                int run = 0;
                boolean last = false;
                int window = 0;   // últimos 11 módulos da linha
                for (int b = 0; b < size; b++) {
                    boolean v = pass == 0 ? m[a][b] : m[b][a];
                    if (pass == 0 && v) dark++;
                    if (b > 0 && v == last) {
                        run++;
                        if (run == 5) score += 3;
                        else if (run > 5) score++;
                    } else {
                        run = 1;
                    }
                    last = v;
                    window = ((window << 1) | (v ? 1 : 0)) & 0x7FF;
                    if (b >= 10 && (window == 0x5D0 || window == 0x05D)) score += 40;
                }
            }
        }
        for (int y = 0; y + 1 < size; y++) {
            for (int x = 0; x + 1 < size; x++) {
                if (m[y][x] == m[y][x + 1] && m[y][x] == m[y + 1][x] && m[y][x] == m[y + 1][x + 1]) score += 3;
            }
        }
        int total = size * size;
        score += 10L * ((Math.abs(dark * 20 - total * 10) + total - 1) / total - 1);
        return score;
    }
}
