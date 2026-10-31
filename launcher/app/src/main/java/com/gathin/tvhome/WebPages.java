package com.gathin.tvhome;

/**
 * As duas páginas servidas na rede local: o controle pelo celular e o backup. Texto em português e inglês.
 * As duas leem o PIN do fragmento da URL do QR (#pin=1234), guardam no localStorage e limpam a barra.
 */
final class WebPages {
    private WebPages() { }

    /** Lê o PIN do QR (#pin=1234), guarda e tira o fragmento da barra de endereço. */
    static final String PIN_FROM_URL = "(()=>{const h=new URLSearchParams(location.hash.slice(1)).get('pin');"
            + "if(h&&/^[0-9]{4}$/.test(h))localStorage.setItem('pin',h);"
            + "if(location.hash)history.replaceState(null,'',location.pathname+location.search)})();";

    private static final String HEAD = "<!doctype html><html><head><meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1,user-scalable=no'>"
            + "<title>CleanYourTV</title><style>"
            + ":root{color-scheme:dark}*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}"
            + "body{margin:0;font:17px system-ui,sans-serif;background:#000;color:#f5f5f7;padding:20px;max-width:440px;margin:auto}"
            + "h1{font-size:22px;margin:4px 0 16px}"
            + "button,input,textarea{font:inherit;color:inherit;border:0;border-radius:16px;background:#1c1c1e}"
            + "button{padding:16px;min-height:56px}button:active{background:#3a3a3c}"
            + "input,textarea{width:100%;padding:14px;margin:6px 0;border:1px solid #38383a}"
            + ".ok{background:#0a84ff}.ok:active{background:#0060c0}.msg{min-height:22px;color:#a1a1a6;margin:8px 0}"
            + ".wide{width:100%;margin:6px 0}small{color:#a1a1a6}"
            + "</style><script>const pt=(navigator.language||'').startsWith('pt');const T=(a,b)=>pt?a:b;"
            + PIN_FROM_URL + "</script></head><body>";

    private static final String JS = "<script>"
            + "let pin=localStorage.getItem('pin')||'';"
            + "function msg(t){document.getElementById('m').textContent=t}"
            + "async function api(path,body,raw){"
            + "const r=await fetch(path,{method:body===undefined?'GET':'POST',headers:{'X-Pin':pin},body});"
            + "if(r.status==401||r.status==429){msg(r.status==429?T('Muitas tentativas. Aguarde 1 minuto.','Too many tries. Wait 1 minute.'):T('PIN incorreto.','Wrong PIN.'));return null}"
            + "if(!r.ok){msg(await r.text());return null}return raw?r:await r.text()}"
            + "function setPin(){pin=document.getElementById('pin').value.trim();"
            + "api('/api/ping').then(x=>{if(x!==null){localStorage.setItem('pin',pin);document.getElementById('login').hidden=true;document.getElementById('app').hidden=false;msg('')}})}"
            + "window.onload=()=>{document.getElementById('pin').value=pin;if(pin)setPin()}"
            + "</script>";

    private static final String LOGIN = "<div id=login><p>"
            + "<script>document.write(T('Digite o PIN mostrado na TV.','Enter the PIN shown on the TV.'))</script></p>"
            + "<input id=pin inputmode=numeric maxlength=4 placeholder=PIN autocomplete=off>"
            + "<button class='ok wide' onclick=setPin()>OK</button></div>";

    private static final String REMOTE_CSS = """
            :root{color-scheme:dark;--glass:#1C1C1E;--glass2:#2C2C2E;--press:#3A3A3C;--t1:#F5F5F7;--t2:#A1A1A6;
            --acc:#0A84FF;--ease:cubic-bezier(.2,.8,.2,1);--fast:120ms;--mid:240ms}
            *{box-sizing:border-box;-webkit-tap-highlight-color:transparent;-webkit-touch-callout:none;
            -webkit-user-select:none;user-select:none}
            html,body{height:100%}
            body{margin:0;background:#000;color:var(--t1);overscroll-behavior:none;touch-action:manipulation;
            font:17px/1.3 -apple-system,BlinkMacSystemFont,system-ui,"Segoe UI",Roboto,sans-serif}
            [hidden]{display:none!important}
            main{max-width:430px;min-height:100%;margin:0 auto;display:flex;flex-direction:column;gap:20px;
            padding:calc(env(safe-area-inset-top) + 8px) calc(env(safe-area-inset-right) + 20px)
            calc(env(safe-area-inset-bottom) + 24px) calc(env(safe-area-inset-left) + 20px)}
            button{font:inherit;color:inherit;cursor:pointer}
            button:focus-visible,input:focus-visible{outline:2px solid var(--acc);outline-offset:3px}
            svg{width:26px;height:26px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
            header{display:flex;align-items:center;justify-content:space-between;min-height:44px}
            .brand{font-weight:600}
            .st{display:flex;align-items:center;gap:8px;color:var(--t2);font-size:15px}
            .dot{width:8px;height:8px;border-radius:50%;background:#30D158;transition:background var(--mid)}
            .st.off .dot{background:#FF453A}
            .seg{display:grid;grid-template-columns:repeat(3,1fr);gap:2px;padding:3px;border-radius:12px;background:var(--glass)}
            .seg button{min-height:44px;border:0;border-radius:9px;background:none;color:var(--t2);font-weight:600;font-size:15px;
            transition:background var(--mid) var(--ease),color var(--mid)}
            .seg button[aria-selected=true]{background:var(--press);color:var(--t1)}
            .pad{position:relative;width:100%;height:min(calc(100vw - 40px),46vh,390px);border-radius:32px;overflow:hidden;
            touch-action:none;cursor:pointer;background:radial-gradient(130% 90% at 50% 0%,#2C2C2E 0%,#1C1C1E 72%);
            box-shadow:inset 0 0 0 1px rgba(255,255,255,.07);transition:transform var(--fast) var(--ease)}
            .pad.press{transform:scale(.985)}
            .pad .ch{position:absolute;display:grid;width:26px;height:26px;color:rgba(255,255,255,.2);transition:color var(--mid),transform var(--mid) var(--ease)}
            .pad .ch.on{color:var(--t1);transition-duration:60ms}
            .ch.u{top:16px;left:50%;transform:translateX(-50%) rotate(-90deg)}
            .ch.d{bottom:16px;left:50%;transform:translateX(-50%) rotate(90deg)}
            .ch.l{left:16px;top:50%;transform:translateY(-50%) rotate(180deg)}
            .ch.r{right:16px;top:50%;transform:translateY(-50%)}
            .pad .mid{position:absolute;left:50%;top:50%;width:8px;height:8px;margin:-4px;border-radius:50%;background:rgba(255,255,255,.2)}
            .pad .hint{position:absolute;left:0;right:0;bottom:48px;text-align:center;font-size:13px;color:var(--t2);
            transition:opacity .6s}
            .rip{position:absolute;width:96px;height:96px;margin:-48px;border-radius:50%;pointer-events:none;
            background:radial-gradient(circle,rgba(255,255,255,.22),rgba(255,255,255,0) 70%);animation:rip .5s var(--ease) forwards}
            @keyframes rip{from{transform:scale(.3);opacity:1}to{transform:scale(1.8);opacity:0}}
            .keys{display:grid;grid-template-columns:repeat(3,72px);justify-content:space-between;row-gap:20px;padding:0 8px}
            .c{width:72px;height:72px;border:0;border-radius:50%;background:var(--glass);display:grid;place-items:center;
            transition:background var(--fast),transform var(--fast) var(--ease)}
            .c:active,.c.down{background:var(--press);transform:scale(.93)}
            .vol{grid-row:span 2;width:72px;border-radius:36px;background:var(--glass);display:flex;flex-direction:column;overflow:hidden}
            .vol button{flex:1;border:0;background:none;display:grid;place-items:center;transition:background var(--fast)}
            .vol button:active,.vol button.down{background:var(--press)}
            .vol i{height:1px;margin:0 22px;background:rgba(255,255,255,.1)}
            .apps{display:grid;grid-template-columns:repeat(4,1fr);gap:20px 8px}
            .app{border:0;background:none;display:flex;flex-direction:column;align-items:center;gap:8px;min-height:44px;padding:0}
            .app i{display:block;width:64px;height:64px;border-radius:16px;overflow:hidden;background:var(--glass);
            transition:transform var(--fast) var(--ease)}
            .app:active i{transform:scale(.92)}
            .app img{display:block;width:100%;height:100%;object-fit:cover}
            .app span{max-width:78px;font-size:12px;color:var(--t2);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .empty{color:var(--t2);text-align:center;grid-column:1/-1}
            .card{display:flex;flex-direction:column;gap:10px;padding:16px;border-radius:20px;background:var(--glass)}
            .card label{font-size:13px;font-weight:600;color:var(--t2);letter-spacing:.04em;text-transform:uppercase}
            .card small{font-size:13px;color:var(--t2)}
            .f{display:flex;gap:8px}
            input{flex:1;min-width:0;min-height:48px;padding:0 14px;border:0;border-radius:12px;background:var(--glass2);
            color:var(--t1);font:inherit;font-size:17px;-webkit-user-select:text;user-select:text}
            .go{min-width:64px;min-height:48px;padding:0 16px;border:0;border-radius:12px;background:var(--acc);color:#fff;font-weight:600;
            transition:opacity var(--fast)}
            .go:active{opacity:.7}
            #login{flex:1;display:flex;flex-direction:column;justify-content:center;gap:16px;text-align:center}
            #login h1{margin:0;font-size:28px}
            #login p{margin:0;color:var(--t2)}
            #pin{width:100%;min-height:64px;text-align:center;font-size:32px;letter-spacing:.5em;padding-left:.5em}
            #lm{min-height:22px;color:#FF453A}
            .big{min-height:52px;font-size:17px;border-radius:14px}
            .toast{position:fixed;left:50%;bottom:calc(env(safe-area-inset-bottom) + 24px);max-width:calc(100% - 40px);padding:12px 20px;
            border-radius:999px;background:rgba(58,58,60,.92);-webkit-backdrop-filter:blur(20px);backdrop-filter:blur(20px);font-size:15px;
            opacity:0;transform:translate(-50%,16px);transition:opacity var(--mid),transform var(--mid) var(--ease);pointer-events:none}
            .toast.show{opacity:1;transform:translate(-50%,0)}
            @media (prefers-reduced-motion:reduce){*{transition:none!important;animation:none!important}}
            """;

    private static final String SVG_CHEVRON = "<svg viewBox='0 0 24 24' aria-hidden=true><path d='M9 6l6 6-6 6'/></svg>";

    private static String icon(String paths) {
        return "<svg viewBox='0 0 24 24' aria-hidden=true>" + paths + "</svg>";
    }

    private static final String REMOTE_BODY = "<main>"
            + "<header><span class=brand>CleanYourTV</span>"
            + "<span class=st id=st role=status><span class=dot></span><span id=stt></span></span></header>"
            + "<section id=login hidden><h1 data-t='Controle da TV|TV Remote'></h1>"
            + "<p data-t='Digite o PIN mostrado na TV.|Enter the PIN shown on the TV.'></p>"
            + "<form onsubmit='connect();return false'><input id=pin inputmode=numeric pattern='[0-9]*' maxlength=4 "
            + "autocomplete=one-time-code aria-label=PIN placeholder='••••'></form>"
            + "<button class='go big' onclick=connect() data-t='Conectar|Connect'></button><div id=lm role=alert></div></section>"
            + "<section id=app hidden style='display:flex;flex-direction:column;gap:20px'>"
            + "<nav class=seg role=tablist>"
            + "<button role=tab data-v=remote aria-selected=true data-t='Controle|Remote'></button>"
            + "<button role=tab data-v=apps aria-selected=false data-t='Apps|Apps'></button>"
            + "<button role=tab data-v=kbd aria-selected=false data-t='Teclado|Keyboard'></button></nav>"
            + "<div data-view=remote style='display:flex;flex-direction:column;gap:28px'>"
            + "<div class=pad id=pad role=button tabindex=0 data-a='Touchpad: deslize para navegar, toque para OK|Touchpad: swipe to move, tap for OK'>"
            + "<span class='ch u'>" + SVG_CHEVRON + "</span><span class='ch d'>" + SVG_CHEVRON + "</span>"
            + "<span class='ch l'>" + SVG_CHEVRON + "</span><span class='ch r'>" + SVG_CHEVRON + "</span>"
            + "<span class=mid></span><span class=hint id=hint data-t='Deslize para navegar · toque para OK|Swipe to move · tap for OK'></span></div>"
            + "<div class=keys>"
            + "<button class=c data-k=back data-a='Voltar|Back'>" + icon("<path d='M15 18l-6-6 6-6'/>") + "</button>"
            + "<button class=c data-k=home data-a='Início|Home'>" + icon("<rect x=3 y=5 width=18 height=12 rx=2.5 /><path d='M8 21h8'/>") + "</button>"
            + "<div class=vol><button data-k=volup data-rep data-a='Aumentar volume|Volume up'>" + icon("<path d='M12 6v12M6 12h12'/>")
            + "</button><i></i><button data-k=voldown data-rep data-a='Diminuir volume|Volume down'>" + icon("<path d='M6 12h12'/>") + "</button></div>"
            + "<button class=c data-k=playpause data-a='Reproduzir ou pausar|Play or pause'>"
            + icon("<path d='M4 6l8 6-8 6z' fill=currentColor /><path d='M16 6v12M20 6v12'/>") + "</button>"
            + "<button class=c data-k=mute data-a='Mudo|Mute'>"
            + icon("<path d='M11 5L6 9H3v6h3l5 4z'/><path d='M22 9l-6 6M16 9l6 6'/>") + "</button>"
            + "<button class=c data-k=prev data-a='Anterior|Previous'>" + icon("<path d='M19 6l-9 6 9 6z' fill=currentColor /><path d='M6 6v12'/>") + "</button>"
            + "<button class=c id=find data-a='Buscar no YouTube|Search YouTube'>"
            + icon("<circle cx=11 cy=11 r=7 /><path d='M20 20l-3.8-3.8'/>") + "</button>"
            + "<button class=c data-k=next data-a='Próximo|Next'>" + icon("<path d='M5 6l9 6-9 6z' fill=currentColor /><path d='M18 6v12'/>") + "</button>"
            + "</div></div>"
            + "<div data-view=apps hidden><div class=apps id=apps></div></div>"
            + "<div data-view=kbd hidden style='display:flex;flex-direction:column;gap:16px'>"
            + "<form class=card onsubmit=\"return send('/api/search','q',T('Buscando no YouTube','Searching YouTube'),1)\">"
            + "<label for=q data-t='Buscar no YouTube|Search YouTube'></label><div class=f>"
            + "<input id=q type=search enterkeyhint=search autocomplete=off data-p='O que você quer ver?|What do you want to watch?'>"
            + "<button class=go data-t='Buscar|Search'></button></div></form>"
            + "<form class=card onsubmit=\"return send('/api/text','txt',T('Texto enviado','Text sent'),0)\">"
            + "<label for=txt data-t='Digitar na TV|Type on the TV'></label><div class=f>"
            + "<input id=txt enterkeyhint=send autocomplete=off data-p='Texto|Text'>"
            + "<button class=go data-t='Enviar|Send'></button></div>"
            + "<small data-t='Vai para o campo de texto selecionado na TV.|Goes to the text field selected on the TV.'></small></form>"
            + "<form class=card onsubmit=\"return send('/api/link','lnk',T('Abrindo na TV','Opening on the TV'),1)\">"
            + "<label for=lnk data-t='Abrir link na TV|Open link on the TV'></label><div class=f>"
            + "<input id=lnk type=url inputmode=url enterkeyhint=go autocomplete=off placeholder='https://'>"
            + "<button class=go data-t='Abrir|Open'></button></div>"
            + "<small data-t='Links do YouTube abrem no TizenTube.|YouTube links open in TizenTube.'></small></form>"
            + "</div></section></main><div class=toast id=toast role=status aria-live=polite></div>";

    private static final String REMOTE_JS = """
            const pt=(navigator.language||'').toLowerCase().startsWith('pt');const T=(a,b)=>pt?a:b;
            const $=id=>document.getElementById(id);
            let pin=localStorage.getItem('pin')||'';
            function i18n(){document.documentElement.lang=pt?'pt-BR':'en';
            const pick=s=>{const p=s.split('|');return T(p[0],p[1])};
            document.querySelectorAll('[data-t]').forEach(e=>e.textContent=pick(e.dataset.t));
            document.querySelectorAll('[data-a]').forEach(e=>e.setAttribute('aria-label',pick(e.dataset.a)));
            document.querySelectorAll('[data-p]').forEach(e=>e.placeholder=pick(e.dataset.p))}
            let tt;function toast(t){const e=$('toast');e.textContent=t;e.classList.add('show');clearTimeout(tt);
            tt=setTimeout(()=>e.classList.remove('show'),1800)}
            function status(ok,t){$('st').classList.toggle('off',!ok);$('stt').textContent=t}
            function buzz(ms){try{if(navigator.vibrate)navigator.vibrate(ms)}catch(e){}}
            function showLogin(m){$('app').hidden=true;$('login').hidden=false;$('lm').textContent=m||'';$('st').hidden=true}
            async function api(path,body,o){o=o||{};let r;
            try{r=await fetch(path,{method:body===undefined?'GET':'POST',headers:{'X-Pin':pin},body})}
            catch(e){status(false,T('Sem conexão','Offline'));return null}
            if(r.status==401){localStorage.removeItem('pin');showLogin(T('PIN incorreto.','Wrong PIN.'));return null}
            if(r.status==429){showLogin(T('Muitas tentativas. Aguarde 1 minuto.','Too many tries. Wait 1 minute.'));return null}
            if(!r.ok){if(!o.quiet)toast(T('Não deu certo.','That did not work.'));return null}
            return o.raw?r:await r.text()}
            function connect(){const v=$('pin').value.trim();if(v)pin=v;
            api('/api/ping').then(x=>{if(x===null)return;localStorage.setItem('pin',pin);$('login').hidden=true;$('app').hidden=false;$('st').hidden=false;
            status(true,T('Conectado','Connected'))})}
            let q=Promise.resolve();
            function key(k){q=q.then(()=>api('/api/key?k='+k,'')).then(x=>{
            if(x==='off')status(false,T('Ative a acessibilidade na TV','Turn on accessibility on the TV'));
            else if(x!==null)status(true,T('Conectado','Connected'))})}
            function tab(n){document.querySelectorAll('.seg button').forEach(b=>b.setAttribute('aria-selected',b.dataset.v==n));
            document.querySelectorAll('[data-view]').forEach(v=>v.hidden=v.dataset.view!=n);if(n=='apps'&&!appsLoaded)loadApps()}
            let appsLoaded=false;
            async function loadApps(){appsLoaded=true;const x=await api('/api/apps');if(x===null){appsLoaded=false;return}
            const list=JSON.parse(x),g=$('apps'),todo=[];g.textContent='';
            if(!list.length){const p=document.createElement('p');p.className='empty';p.textContent=T('Nenhum app.','No apps.');g.append(p);return}
            for(const a of list){const b=document.createElement('button'),i=document.createElement('i'),s=document.createElement('span');
            s.textContent=a.n;s.setAttribute('aria-hidden','true');b.className='app';b.setAttribute('aria-label',a.n);b.append(i,s);
            b.onclick=()=>{buzz(10);api('/api/open?p='+encodeURIComponent(a.p),'').then(r=>{if(r!==null)toast(T('Abrindo ','Opening ')+a.n)})};
            g.append(b);todo.push([a.p,i])}
            const work=async()=>{while(todo.length){const[p,i]=todo.shift();
            const r=await api('/api/icon?p='+encodeURIComponent(p),undefined,{raw:1,quiet:1});
            if(r){const img=new Image();img.alt='';img.src=URL.createObjectURL(await r.blob());i.append(img)}}};
            work();work();work()}
            function send(path,id,done,clear){const e=$(id),v=e.value.trim();if(!v)return false;
            api(path,v).then(x=>{if(x===null)return;buzz(10);toast(done);if(clear){e.value='';e.blur()}});return false}
            function flash(d){const c=document.querySelector('.ch.'+d[0]);c.classList.add('on');setTimeout(()=>c.classList.remove('on'),160)}
            function ripple(x,y){const pad=$('pad'),b=pad.getBoundingClientRect(),s=document.createElement('span');s.className='rip';
            s.style.left=(x-b.left)+'px';s.style.top=(y-b.top)+'px';pad.append(s);setTimeout(()=>s.remove(),520)}
            function dir(dx,dy){return Math.abs(dx)>Math.abs(dy)?(dx>0?'right':'left'):(dy>0?'down':'up')}
            function move(d){key(d);flash(d);buzz(5)}
            function setupPad(){const pad=$('pad'),STEP=44,FLICK=22;let st=null;
            pad.addEventListener('pointerdown',e=>{pad.setPointerCapture(e.pointerId);st={x:e.clientX,y:e.clientY,x0:e.clientX,y0:e.clientY,t:Date.now(),moved:false};
            ripple(e.clientX,e.clientY);$('hint').style.opacity=0});
            pad.addEventListener('pointermove',e=>{if(!st)return;const dx=e.clientX-st.x,dy=e.clientY-st.y;
            if(Math.max(Math.abs(dx),Math.abs(dy))<STEP)return;move(dir(dx,dy));st.x=e.clientX;st.y=e.clientY;st.moved=true});
            pad.addEventListener('pointerup',e=>{if(!st)return;const dx=e.clientX-st.x0,dy=e.clientY-st.y0,
            far=Math.max(Math.abs(dx),Math.abs(dy)),quick=Date.now()-st.t<350;
            if(!st.moved){if(far>=FLICK&&quick)move(dir(dx,dy));else if(far<FLICK){key('ok');buzz(12);
            pad.classList.add('press');setTimeout(()=>pad.classList.remove('press'),120)}}st=null});
            pad.addEventListener('pointercancel',()=>st=null);
            pad.addEventListener('keydown',e=>{if(e.key=='Enter'||e.key==' '){e.preventDefault();key('ok')}})}
            function hold(el,fn){let t1,t2;const stop=()=>{clearTimeout(t1);clearInterval(t2);el.classList.remove('down')};
            el.addEventListener('pointerdown',e=>{e.preventDefault();el.classList.add('down');fn();buzz(6);
            t1=setTimeout(()=>{t2=setInterval(fn,160)},450)});
            ['pointerup','pointerleave','pointercancel'].forEach(n=>el.addEventListener(n,stop));
            el.addEventListener('click',e=>{if(e.detail===0)fn()})}
            const ARROWS={ArrowUp:'up',ArrowDown:'down',ArrowLeft:'left',ArrowRight:'right',Escape:'back',Backspace:'back'};
            document.addEventListener('keydown',e=>{if($('app').hidden||e.target.tagName=='INPUT'||document.querySelector('[data-view=remote]').hidden)return;
            const k=ARROWS[e.key];if(k){e.preventDefault();key(k);if(k!='back')flash(k)}});
            window.addEventListener('load',()=>{i18n();setupPad();
            document.querySelectorAll('.seg button').forEach(b=>b.onclick=()=>tab(b.dataset.v));
            document.querySelectorAll('[data-k]').forEach(b=>{const k=b.dataset.k;
            if(b.hasAttribute('data-rep'))hold(b,()=>key(k));else b.onclick=()=>{key(k);buzz(8)}});
            $('find').onclick=()=>{buzz(8);tab('kbd');$('q').focus()};
            if(pin){$('pin').value=pin;connect()}else showLogin()});
            """;

    static String remote() {
        return "<!doctype html><html lang=en><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover'>"
                + "<meta name=theme-color content='#000000'><meta name=apple-mobile-web-app-capable content=yes>"
                + "<meta name=apple-mobile-web-app-status-bar-style content=black>"
                + "<title>CleanYourTV Remote</title><style>" + REMOTE_CSS + "</style>"
                + "<script>" + PIN_FROM_URL + "</script></head><body>"
                + REMOTE_BODY + "<script>" + REMOTE_JS + "</script></body></html>";
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
