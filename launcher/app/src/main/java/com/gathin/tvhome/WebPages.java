package com.gathin.tvhome;

/** As duas páginas servidas na rede local: o controle pelo celular e o backup. Texto em português e inglês. */
final class WebPages {
    private WebPages() { }

    private static final String HEAD = "<!doctype html><html><head><meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1,user-scalable=no'>"
            + "<title>CleanYourTV</title><style>"
            + ":root{color-scheme:dark}*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}"
            + "body{margin:0;font:17px system-ui,sans-serif;background:#000;color:#f5f5f7;padding:20px;max-width:440px;margin:auto}"
            + "h1{font-size:22px;margin:4px 0 16px}"
            + "button,input,textarea{font:inherit;color:inherit;border:0;border-radius:16px;background:#1c1c1e}"
            + "button{padding:16px;min-height:56px}button:active{background:#3a3a3c}"
            + "input,textarea{width:100%;padding:14px;margin:6px 0;border:1px solid #38383a}"
            + ".grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:14px 0}"
            + ".row{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:10px 0}"
            + ".ok{background:#0a84ff}.ok:active{background:#0060c0}.msg{min-height:22px;color:#a1a1a6;margin:8px 0}"
            + ".wide{width:100%;margin:6px 0}small{color:#a1a1a6}"
            + "</style><script>const pt=(navigator.language||'').startsWith('pt');const T=(a,b)=>pt?a:b</script></head><body>";

    private static final String JS = "<script>"
            + "let pin=sessionStorage.getItem('pin')||'';"
            + "function msg(t){document.getElementById('m').textContent=t}"
            + "async function api(path,body,raw){"
            + "const r=await fetch(path,{method:body===undefined?'GET':'POST',headers:{'X-Pin':pin},body});"
            + "if(r.status==401||r.status==429){msg(r.status==429?T('Muitas tentativas. Aguarde 1 minuto.','Too many tries. Wait 1 minute.'):T('PIN incorreto.','Wrong PIN.'));return null}"
            + "if(!r.ok){msg(await r.text());return null}return raw?r:await r.text()}"
            + "function setPin(){pin=document.getElementById('pin').value.trim();sessionStorage.setItem('pin',pin);"
            + "api('/api/ping').then(x=>{if(x!==null){document.getElementById('login').hidden=true;document.getElementById('app').hidden=false;msg('')}})}"
            + "window.onload=()=>{document.getElementById('pin').value=pin;if(pin)setPin()}"
            + "</script>";

    private static final String LOGIN = "<div id=login><p>"
            + "<script>document.write(T('Digite o PIN mostrado na TV.','Enter the PIN shown on the TV.'))</script></p>"
            + "<input id=pin inputmode=numeric maxlength=4 placeholder=PIN autocomplete=off>"
            + "<button class='ok wide' onclick=setPin()>OK</button></div>";

    static String remote() {
        return HEAD + "<h1>CleanYourTV</h1>" + LOGIN + "<div id=app hidden>"
                + "<div class=grid><span></span><button onclick=\"k('up')\">&#9650;</button><span></span>"
                + "<button onclick=\"k('left')\">&#9664;</button><button class=ok onclick=\"k('ok')\">OK</button>"
                + "<button onclick=\"k('right')\">&#9654;</button><span></span><button onclick=\"k('down')\">&#9660;</button><span></span></div>"
                + "<div class=row><button onclick=\"k('back')\">&#8592;</button><button onclick=\"k('home')\">&#8962;</button>"
                + "<button onclick=\"k('mute')\">&#128263;</button></div>"
                + "<div class=row><button onclick=\"k('voldown')\">Vol &minus;</button><span></span>"
                + "<button onclick=\"k('volup')\">Vol +</button></div>"
                + "<input id=txt placeholder=Texto><button class=wide onclick=\"s('/api/text','txt')\">"
                + "<script>document.write(T('Enviar texto','Send text'))</script></button>"
                + "<input id=lnk inputmode=url placeholder='https://'><button class=wide onclick=\"s('/api/link','lnk')\">"
                + "<script>document.write(T('Abrir link na TV','Open link on the TV'))</script></button>"
                + "<small><script>document.write(T('D-pad e texto funcionam nos apps com telas Android; vídeo e jogos podem ignorar.',"
                + "'D-pad and text work in apps with Android screens; video and games may ignore them.'))</script></small></div>"
                + "<div class=msg id=m></div>" + JS
                + "<script>function k(x){api('/api/key?k='+x,'')}"
                + "function s(p,id){const e=document.getElementById(id);if(e.value)api(p,e.value).then(x=>{if(x!==null){msg('OK');if(id=='lnk')e.value=''}})}</script>"
                + "</body></html>";
    }

    static String backup() {
        return HEAD + "<h1>CleanYourTV</h1>" + LOGIN + "<div id=app hidden>"
                + "<button class='ok wide' onclick=dl()><script>document.write(T('Baixar backup','Download backup'))</script></button>"
                + "<p><script>document.write(T('Importar backup (JSON):','Import backup (JSON):'))</script></p>"
                + "<input type=file id=f accept=.json,application/json>"
                + "<button class=wide onclick=up()><script>document.write(T('Enviar para a TV','Send to the TV'))</script></button></div>"
                + "<div class=msg id=m></div>" + JS
                + "<script>async function dl(){const r=await api('/api/export',undefined,true);if(!r)return;"
                + "const b=await r.blob();const a=document.createElement('a');a.href=URL.createObjectURL(b);"
                + "a.download='cleanyourtv-backup.json';a.click();msg('OK')}"
                + "async function up(){const f=document.getElementById('f').files[0];if(!f){msg(T('Escolha um arquivo.','Choose a file.'));return}"
                + "const x=await api('/api/import',await f.text());if(x!==null)msg(x)}</script></body></html>";
    }
}
