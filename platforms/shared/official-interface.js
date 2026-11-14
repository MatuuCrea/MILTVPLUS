import { optimizer } from "./performance-optimizer.js";

const NAV = [
  ["Inicio","⌂"],["TV en Vivo","▣"],["Películas","▧"],["Series","▤"],["Deportes","🏆"],
  ["Infantil","☺"],["Documentales","▤"],["Música","♫"],["Adultos 18+","18+"],
  ["Favoritos","☆"],["Configuración","⚙"]
];
const COUNTRIES = [
  ["AR","🇦🇷","Argentina","Siempre en tu corazón"],
  ["US","🇺🇸","Estados Unidos","Un mundo de historias"],
  ["ES","🇪🇸","España","Cultura que nos une"],
  ["MX","🇲🇽","México","Tradición y emoción"],
  ["CO","🇨🇴","Colombia","Pasión que se comparte"],
  ["CL","🇨🇱","Chile","Un país, mil paisajes"],
  ["BR","🇧🇷","Brasil","Ritmo y alegría"]
];
const $ = id => document.getElementById(id);
const node = (tag, cls, text) => { const el=document.createElement(tag); if(cls)el.className=cls; if(text!==undefined)el.textContent=text; return el; };

export function mountOfficialInterface({ onCountryChange=()=>{}, loadEpg=async()=>[], getChannels=()=>[], getSelectedChannel=()=>null, getCountryCounts=async()=>({}) }={}) {
  const main=$("main") || document.querySelector("main");
  const channels=$("channels"), empty=$("empty"), header=document.querySelector("header");
  if(!main||!channels||!header||$("official-sidebar")) return ()=>{};
  let active="TV en Vivo", selectedCountry=localStorage.getItem("miltvplus.country")||"AR";
  let category="all", epgOffset=0, selectedProgram=null;
  const sidebar=node("aside","official-sidebar"); sidebar.id="official-sidebar";
  const logo=node("div","official-logo"); logo.innerHTML='<span>MIL</span><b>TV</b><strong>PLUS</strong><small>Tu mundo en una sola app 🇦🇷</small>';
  sidebar.append(logo);
  const nav=node("nav","official-nav"); nav.setAttribute("aria-label","Navegación principal");
  NAV.forEach(([label,icon])=>{const b=node("button","official-nav-item");b.type="button";b.dataset.section=label;const i=node("span","nav-icon",icon);const t=node("span","nav-label",label);b.append(i,t);b.addEventListener("click",()=>show(label));nav.append(b);});
  const more=node("button","official-nav-item");more.type="button";more.innerHTML='<span class="nav-icon">•••</span><span class="nav-label">Más Opciones</span><span class="nav-chevron">⌄</span>';
  const extras=node("div","official-more");extras.hidden=true;
  [["Guía EPG","▦"],["Países","◎"],["Fuentes","＋"]].forEach(([label,icon])=>{const b=node("button","official-nav-extra",icon+"  "+label);b.addEventListener("click",()=>show(label));extras.append(b)});
  more.addEventListener("click",()=>extras.hidden=!extras.hidden);nav.append(more,extras);sidebar.append(nav);
  const brand=node("section","official-brand-card");brand.innerHTML='<div class="brand-crown">♛</div><strong>MILTVPLUS Premium</strong><span>Experiencia completa · 100 % gratuita</span><small>Sin anuncios · Sin pagos · Sin suscripciones</small><b>🇦🇷</b>';sidebar.append(brand);
  document.body.insertBefore(sidebar,main);

  const top=node("div","official-toolbar");
  const search=node("input","official-search");search.type="search";search.placeholder="⌕  Buscar en MILTVPLUS…";search.setAttribute("aria-label","Buscar en MILTVPLUS");
  const filter=node("button","official-filter","☷  Filtros");filter.type="button";
  const countBadge=node("span","official-count","0 canales");
  top.append(search,filter,countBadge);main.insertBefore(top,main.querySelector("h1")?.nextSibling||channels);
  const tvCats=node("nav","tv-categories");tvCats.setAttribute("aria-label","Categorías de TV en Vivo");
  const categories=[["Todos","▦","all"],["Noticias","▤","news"],["Deportes","🏆","sports"],["Entretenimiento","★","entertainment"],["Infantiles","☺","kids"],["Películas","▧","movies"],["Documentales","▤","documentary"],["Música","♫","music"],["Religiosos","✝","religious"],["Locales","⌖","local"]];
  categories.forEach(([label,icon,key])=>{const b=node("button","tv-category",icon+"  "+label);b.type="button";b.dataset.category=key;b.addEventListener("click",()=>{category=key;tvCats.querySelectorAll("button").forEach(x=>x.classList.toggle("active",x===b));filterChannels()});tvCats.append(b)});
  main.insertBefore(tvCats,channels);
  const home=node("section","official-page official-home");home.hidden=true;home.innerHTML='<div class="home-hero"><div class="hero-copy"><small>🇦🇷 MILTVPLUS · ARGENTINA Y EL MUNDO</small><h2>Bienvenido a<br><b>MILTVPLUS</b></h2><p>La mejor experiencia de entretenimiento en Argentina y el mundo.</p><p>✓ TV en Vivo · Películas · Series · Deportes<br>✓ Canales públicos y fuentes autorizadas<br>✓ Guía EPG, favoritos y mucho más</p><button type="button" data-go="TV en Vivo">▶ Ver Televisión</button></div><div class="hero-flag">☀<br>🇦🇷<br><small>ARGENTINA</small></div></div>';
  const adult=node("section","official-page adult-page");adult.hidden=true;adult.innerHTML='<div class="adult-hero"><div class="adult-mark">18+<small>Solo para adultos</small></div><div class="adult-copy"><small>♛ CONTENIDO PRIVADO</small><h2>Contenido <b>Adulto</b></h2><p>Experiencia privada y protegida para mayores de 18 años.</p><button type="button" disabled>Ingresar ahora · Temporalmente bloqueado</button></div><div class="adult-script">Pasión<br>sin límites ♡</div></div><div class="adult-categories"></div><h3>▶ Recomendados para vos</h3><div class="adult-empty">El catálogo +18 permanece vacío y protegido. No hay fuentes ni reproducciones activas.</div><div class="adult-footer">🛡 Contenido seguro　│　🔒 Acceso +18　│　♛ Calidad según la fuente</div>';
  const adultCats=[["💋","Romance"],["🔥","Eróticos"],["💞","Parejas"],["⭐","Hot"],["💎","Premium"],["🔞","Solo Adultos"]];
  const adultCatRoot=adult.querySelector(".adult-categories");adultCats.forEach(([icon,label])=>adultCatRoot.append(node("div","adult-category",icon+"\n"+label)));
  const countries=node("section","official-page countries-page");countries.hidden=true;countries.innerHTML='<h2>◎ Seleccionar País</h2><input class="country-search" placeholder="Buscar país…"><div class="country-list"></div><div class="country-identity"></div>';
  const settings=node("section","official-page settings-page");settings.hidden=true;settings.innerHTML='<h2>⚙ Configuración</h2><div class="settings-layout"><nav class="settings-sections"></nav><div class="settings-options"></div></div><button type="button" class="settings-reset">↻ Restablecer Configuración</button>';
  const epg=node("section","official-page epg-page");epg.hidden=true;epg.innerHTML='<h2>▦ Guía de Programación (EPG)</h2><div class="epg-days"></div><div class="epg-grid"></div><div class="epg-actions"><button data-action="record" disabled title="La grabación requiere soporte de captura del dispositivo y una fuente autorizada">● Grabar</button><button data-action="remind">◉ Recordatorio</button><button data-action="next24">+24 h</button><button data-action="info">Info</button></div><p class="epg-status" role="status"></p>';
  main.append(home,adult,countries,settings,epg);
  home.querySelector("[data-go]").addEventListener("click",()=>show("TV en Vivo"));
  const playerHead=document.querySelector(".player-head");
  if(playerHead){
    const actions=node("div","player-actions");
    const epgButton=node("button","","▦ EPG");epgButton.addEventListener("click",()=>{$("close-player")?.click();show("Guía EPG")});
    const favoriteButton=node("button","","♡ Favorito");favoriteButton.addEventListener("click",()=>{const item=getSelectedChannel();if(!item?.id)return;const ids=new Set(JSON.parse(localStorage.getItem("miltvplus.favorites")||"[]"));if(ids.has(item.id)){ids.delete(item.id);favoriteButton.textContent="♡ Favorito"}else{ids.add(item.id);favoriteButton.textContent="♥ Favorito"}localStorage.setItem("miltvplus.favorites",JSON.stringify([...ids]))});
    const recordButton=node("button","","● Grabar");recordButton.disabled=true;recordButton.title="La grabación requiere hardware de captura y permisos de la fuente.";
    const moreButton=node("button","","••• Más");moreButton.addEventListener("click",()=>{const item=getSelectedChannel();$("player-message").textContent=item?.description||"No hay más información publicada para este canal."});
    actions.append(epgButton,favoriteButton,recordButton,moreButton);playerHead.append(actions);
  }

  function show(label){
    if($("player")&&!$("player").hidden&&label!=="TV en Vivo") $("close-player")?.click();
    active=label;
    const musicSection=$("music-experience");
    const musicNav=$("nav-music");
    const isMusic=label==="Música";
    if(musicSection)musicSection.hidden=!isMusic;
    if(isMusic){musicNav?.click();main.querySelector("h1").textContent="Música"}
    else {if($("nav-tv") && musicSection) $("nav-tv").click();}
    const page=label==="Inicio"?home:label==="Adultos 18+"?adult:label==="Países"?countries:label==="Configuración"?settings:label==="Guía EPG"?epg:null;
    [home,adult,countries,settings,epg].forEach(p=>p.hidden=p!==page);
    const showTv=!page&&!isMusic;
    if(label==="Deportes") category="sports";
    else if(label==="Infantil") category="kids";
    else if(label==="Documentales") category="documentary";
    else if(label==="TV en Vivo"||label==="Inicio") category="all";
    channels.hidden=!showTv;empty.hidden=!showTv;
    top.hidden=!showTv&&!isMusic;
    tvCats.hidden=!(label==="TV en Vivo"||label==="Deportes"||label==="Infantil"||label==="Documentales");
    if(showTv){main.querySelector("h1").textContent=label;filterChannels()}
    main.querySelector("p")?.toggleAttribute("hidden",!showTv);
    sidebar.querySelectorAll("[data-section]").forEach(b=>b.classList.toggle("active",b.dataset.section===label));
    if(label==="Países")renderCountries();
    if(label==="Configuración")renderSettings();
    if(label==="Guía EPG")renderEpg();
    if(label==="Favoritos")showFavorites();
    if(label==="Películas"||label==="Series") {channels.hidden=true;empty.hidden=false;empty.querySelector("h2").textContent="Catálogo no disponible";empty.querySelector("p").textContent="Esta sección sólo mostrará contenidos publicados con derechos de reproducción verificados."}
    if(label==="Música" && !musicSection) $("nav-music")?.click();
  }
  function filterChannels(){
    const q=search.value.trim().toLowerCase();
    channels.querySelectorAll(".channel").forEach(card=>{
      const text=card.textContent.toLowerCase();
      const matchQ=!q||text.includes(q);
      const item=getChannels()[Number(card.dataset.index)];
      const value=String(item?.category||"").toLowerCase();
      const aliases={sports:["sports","sport","deportes"],kids:["kids","infantil","children"],documentary:["documentary","documentales"],news:["news","noticias"],entertainment:["entertainment","entretenimiento"],movies:["movie","movies","peliculas"],music:["music","musica"],religious:["religious","religioso"],local:["local","locales"]};
      const matchC=category==="all"||(aliases[category]||[category]).some(v=>value.includes(v));
      const favorites=new Set(JSON.parse(localStorage.getItem("miltvplus.favorites")||"[]"));
      const matchFavorite=active!=="Favoritos"||favorites.has(item?.id);
      card.hidden=!(matchQ&&matchC&&matchFavorite);
    });
    const visibleCount=channels.querySelectorAll(".channel:not([hidden])").length;
    countBadge.textContent=visibleCount+" canales";
    if(["Inicio","Adultos 18+","Países","Configuración","Guía EPG","Música","Películas","Series"].includes(active)) empty.hidden=true;
    else empty.hidden=visibleCount>0;
  }
  search.addEventListener("input",filterChannels);
  filter.addEventListener("click",()=>{tvCats.hidden=false;tvCats.scrollIntoView({behavior:"smooth",block:"nearest"});});
  function showFavorites(){filterChannels()}
  async function renderCountries(){
    const list=countries.querySelector(".country-list");list.replaceChildren();
    const counts=await getCountryCounts().catch(()=>({}));
    COUNTRIES.forEach(([code,flag,name,phrase])=>{
      const b=node("button","country-option");b.type="button";const count=Number(counts[code]);
      b.innerHTML='<span class="country-flag">'+flag+'</span><span class="country-name"></span><small class="country-count"></small><span class="country-check"></span>';
      b.querySelector(".country-name").textContent=name;b.querySelector(".country-count").textContent=Number.isFinite(count)?("Canales publicados: "+count):"Canales publicados: —";
      b.querySelector(".country-check").textContent=code===selectedCountry?"✓":"";
      b.addEventListener("click",()=>{selectedCountry=code;localStorage.setItem("miltvplus.country",code);countries.querySelector(".country-identity").textContent=name+" · "+phrase+" "+flag;renderCountries();onCountryChange(code);});
      list.append(b);
    });
    const item=COUNTRIES.find(x=>x[0]===selectedCountry)||COUNTRIES[0];countries.querySelector(".country-identity").textContent=item[2]+" · "+item[3]+" "+item[1];
  }
  countries.querySelector(".country-search").addEventListener("input",e=>{const q=e.target.value.toLowerCase();countries.querySelectorAll(".country-option").forEach(b=>b.hidden=!b.textContent.toLowerCase().includes(q))});
  function renderSettings(){
    const root=settings.querySelector(".settings-options");root.replaceChildren();
    const sections=["General","Reproductor","EPG","Países y Canales","Favoritos","Notificaciones","Acerca de"];
    const nav=settings.querySelector(".settings-sections");nav.replaceChildren();
    const help=node("p","settings-help");
    const groupHelp={General:"Idioma, apariencia y preferencias generales.",Reproductor:"Calidad adaptativa, reproducción automática y subtítulos.",EPG:"La guía muestra únicamente programas publicados. Los recordatorios se guardan localmente.","Países y Canales":"El país seleccionado filtra el catálogo; no concede derechos de reproducción.",Favoritos:"Los canales marcados como favoritos se mantienen en este dispositivo.",Notificaciones:"La preferencia se guarda localmente; las notificaciones push no están conectadas.","Acerca de":"MILTVPLUS · Aplicación gratuita, sin anuncios, pagos ni suscripciones."};
    sections.forEach((s,i)=>{const b=node("button",i===0?"active":"",s);b.type="button";b.addEventListener("click",()=>{nav.querySelectorAll("button").forEach(x=>x.classList.toggle("active",x===b));root.querySelectorAll(".setting-row").forEach(row=>row.hidden=row.dataset.group!==s);help.textContent=groupHelp[s]||"";});nav.append(b)});
    const options=[["General","Idioma de la App","select",["Español","English","Português"]],["Reproductor","Calidad de Video","select",["Automática","Alta","Media","Baja"]],["Reproductor","Reproducción Automática","toggle",true],["Reproductor","Mostrar Subtítulos","toggle",true],["General","Modo Oscuro","toggle",true],["Notificaciones","Notificaciones","toggle",true]];
    options.forEach(([group,label,type,value])=>{const row=node("label","setting-row");row.dataset.group=group;row.append(node("span","",label));let control;if(type==="toggle"){control=document.createElement("input");control.type="checkbox";control.checked=localStorage.getItem("miltvplus.setting."+label)!=="false"&&value===true;control.addEventListener("change",()=>{localStorage.setItem("miltvplus.setting."+label,String(control.checked));if(label==="Modo Oscuro")document.body.classList.toggle("light-mode",!control.checked);if(label==="Mostrar Subtítulos")document.querySelectorAll("video").forEach(v=>Array.from(v.textTracks||[]).forEach(t=>t.mode=control.checked?"showing":"disabled"));});}else{control=document.createElement("select");value.forEach(v=>control.add(new Option(v,v)));control.value=localStorage.getItem("miltvplus.setting."+label)||value[0];control.addEventListener("change",()=>localStorage.setItem("miltvplus.setting."+label,control.value));}row.append(control);root.append(row)});
    root.append(help);root.querySelectorAll(".setting-row").forEach(row=>row.hidden=row.dataset.group!=="General");help.textContent=groupHelp.General;
    settings.querySelector(".settings-reset").onclick=()=>{Object.keys(localStorage).filter(k=>k.startsWith("miltvplus.setting.")).forEach(k=>localStorage.removeItem(k));document.body.classList.remove("light-mode");renderSettings();};
  }
  async function renderEpg(){
    const grid=epg.querySelector(".epg-grid"),days=epg.querySelector(".epg-days");grid.replaceChildren();days.replaceChildren();
    const base=new Date(Date.now()+epgOffset*86400000);
    for(let i=0;i<7;i++){const d=new Date(base.getTime()+i*86400000);days.append(node("span","epg-day",new Intl.DateTimeFormat("es-AR",{weekday:"short",day:"2-digit"}).format(d)))}
    const timeScale=node("div","epg-times");["00:00","06:00","12:00","18:00","24:00"].forEach(t=>timeScale.append(node("span","",t)));grid.append(timeScale);
    epg.querySelector(".epg-status").textContent="Consultando programación publicada…";
    const rows=await loadEpg(base).catch(()=>[]);
    if(!rows.length){epg.querySelector(".epg-status").textContent="No hay programación EPG publicada para este período.";return;}
    const channelsById=new Map();getChannels().forEach(x=>{if(x.slug)channelsById.set(x.slug,x);if(x.id)channelsById.set(x.id,x)});
    const groups=new Map();rows.forEach(p=>{const key=p.channel_external_id||"unknown";if(!groups.has(key))groups.set(key,[]);groups.get(key).push(p)});
    [...groups.entries()].forEach(([channelId,programs])=>{const row=node("article","epg-row");const channel=channelsById.get(channelId);row.append(node("strong","epg-channel",channel?.title||channelId));const timeline=node("div","epg-timeline");programs.sort((x,y)=>Date.parse(x.starts_at)-Date.parse(y.starts_at)).forEach(p=>{const start=new Date(p.starts_at),end=new Date(p.ends_at);const duration=Math.max(30,(end-start)/60000);const card=node("button","epg-program",p.title+"\n"+start.toLocaleTimeString("es-AR",{hour:"2-digit",minute:"2-digit"})+"–"+end.toLocaleTimeString("es-AR",{hour:"2-digit",minute:"2-digit"}));card.style.flexGrow=String(duration);card.style.flexBasis=Math.max(130,duration*1.2)+"px";card.addEventListener("click",()=>{selectedProgram=p;epg.querySelector(".epg-status").textContent=p.title+" — "+(p.description||"Sin descripción publicada")});timeline.append(card)});row.append(timeline);grid.append(row)});
    epg.querySelector(".epg-status").textContent=rows.length+" programas publicados en "+groups.size+" canales.";
  }
  epg.querySelector('[data-action="next24"]').addEventListener("click",()=>{epgOffset+=1;renderEpg()});
  epg.querySelector('[data-action="remind"]').addEventListener("click",()=>{if(!selectedProgram){epg.querySelector(".epg-status").textContent="Seleccioná un programa primero.";return;}const saved=JSON.parse(localStorage.getItem("miltvplus.reminders")||"[]");saved.push({id:selectedProgram.id,title:selectedProgram.title,starts_at:selectedProgram.starts_at});localStorage.setItem("miltvplus.reminders",JSON.stringify(saved));epg.querySelector(".epg-status").textContent="Recordatorio guardado en este dispositivo."});
  epg.querySelector('[data-action="info"]').addEventListener("click",()=>{epg.querySelector(".epg-status").textContent=selectedProgram?.description||"Seleccioná un programa para ver su información."});
  function watchMutations(){const observer=new MutationObserver(filterChannels);observer.observe(channels,{childList:true,subtree:true});return ()=>observer.disconnect()}
  const disposeObserver=watchMutations();
  show("TV en Vivo");
  return ()=>{disposeObserver();optimizer.destruirTodo();};
}
