'use strict';
const animationDraft={frames:[],blob:null,generation:0,files:[],uploading:false,times:[],hasTiming:false,originalTimes:[],gifInfo:[],description:'',uploadTask:Promise.resolve()};
const gifQueue=CapeAnimation.serialQueue();
function animationTimes(){return CapeAnimation.currentTimes(animationDraft.frames.length,Math.max(1,Math.min(200,Number($('animation-ticks').value)||2)),animationDraft.times,animationDraft.hasTiming&&$('animation-gif-timing').checked);}
function updateAnimationTiming(){
 $('animation-gif-settings').hidden=!animationDraft.hasTiming;$('animation-ticks').disabled=animationDraft.hasTiming&&$('animation-gif-timing').checked;
 if(animationDraft.blob){let label=animationDraft.description;if(animationDraft.gifInfo.length){const sourceFrames=animationDraft.gifInfo.reduce((a,b)=>a+b.sourceFrames,0),duration=(animationTimes().reduce((a,b)=>a+b,0)*.05).toFixed(2);label+=' · '+t('web.dynamic.gif-ready',{sourceFrames,frames:animationDraft.frames.length,duration});if(animationDraft.gifInfo.some(g=>g.adapted))label+=t('web.dynamic.gif-adapted');}$('animation-analysis').textContent=label;}
}
async function prepareAnimation(files,existingTimes=null){
 const generation=++animationDraft.generation;animationDraft.files=[...files];animationDraft.frames=[];animationDraft.blob=null;animationDraft.originalTimes=Array.isArray(existingTimes)?[...existingTimes]:[];animationDraft.times=[];animationDraft.hasTiming=false;animationDraft.gifInfo=[];updateAnimationTiming();
 $('animation-upload').disabled=true;$('animation-download').disabled=true;$('animation-metadata').disabled=true;
 const preview=$('animation-preview');preview.getContext('2d').clearRect(0,0,preview.width,preview.height);
 $('animation-analysis').textContent=t(files.length?'web.dynamic.processing':'web.dynamic.empty');
 if(!files.length)return;
 if(files.length>64)throw new Error(t('web.dynamic.limit'));
 const frames=[],times=[],gifInfo=[],outputWidth=Number($('animation-resolution').value),frameHeight=outputWidth/2;
 if(![64,128,256,512].includes(outputWidth))throw new Error(t('web.dynamic.dimensions'));
 const arrangement=$('animation-arrangement').value,layout=$('animation-layout').value;
 const columns=Number($('animation-columns').value),rows=Number($('animation-rows').value);
 let corrected=false;
 const sorted=[...files].sort((a,b)=>a.name.localeCompare(b.name,undefined,{numeric:true}));
 for(const file of sorted){
  const header=new Uint8Array(await file.slice(0,24).arrayBuffer());
  const gif=header.length>=6&&['GIF87a','GIF89a'].includes(String.fromCharCode(...header.slice(0,6)));
  if(file.size>(gif?10:1)*1024*1024)throw new Error(t(gif?'web.dynamic.gif-limit':'web.dynamic.limit'));
  if(gif){
   const fit=$('animation-fit').value;
   const result=await gifQueue(async()=>{await animationDraft.uploadTask;if(generation!==animationDraft.generation)return null;const image=await base64(file);if(generation!==animationDraft.generation)return null;return api('/api/gif',{method:'POST',body:JSON.stringify({image,resolution:String(outputWidth),layout,fit})});});
   if(generation!==animationDraft.generation||!result)return;
   if(frames.length+result.frames>64)throw new Error(t('web.dynamic.limit'));
   const png=new Blob([Uint8Array.from(atob(result.image),c=>c.charCodeAt(0))],{type:'image/png'}),bitmap=await createImageBitmap(png);
   try{const plan=CapeAnimation.plan(bitmap.width,bitmap.height,{layout:'atlas'});for(const rect of plan.rects)frames.push(CapeAnimation.frame(bitmap,rect,'atlas',outputWidth));}finally{bitmap.close();}
   times.push(...result.frameTimes);gifInfo.push({sourceFrames:result.sourceFrames,adapted:result.adapted});continue;
  }
  if(header.length<24||![137,80,78,71,13,10,26,10].every((v,i)=>header[i]===v))throw new Error(t('web.dynamic.gif-file'));
  const info=new DataView(header.buffer),width=info.getUint32(16),height=info.getUint32(20);
  let plan;try{plan=CapeAnimation.plan(width,height,{arrangement,layout,columns,rows});}catch(error){throw new Error(t(error.message));}
  if(frames.length+plan.rects.length>64)throw new Error(t('web.dynamic.limit'));
  const bitmap=await createImageBitmap(file);
  try{
   if(generation!==animationDraft.generation)return;
   for(const rect of plan.rects){frames.push(CapeAnimation.frame(bitmap,rect,plan.layout,outputWidth,$('animation-fit').value));times.push(Math.max(1,Math.min(200,Number($('animation-ticks').value)||2)));}
   corrected||=plan.adjusted;
  }finally{bitmap.close();}
 }
 if(frames.length<2)throw new Error(t('web.dynamic.minimum'));
 const strip=document.createElement('canvas');strip.width=outputWidth;strip.height=frameHeight*frames.length;
 frames.forEach((frame,index)=>strip.getContext('2d').drawImage(frame,0,index*frameHeight));
 const blob=await new Promise(resolve=>strip.toBlob(resolve,'image/png'));
 if(!blob||blob.size>1024*1024)throw new Error(t('web.dynamic.limit'));
 if(generation!==animationDraft.generation)return;
 animationDraft.frames=frames;animationDraft.blob=blob;animationDraft.times=animationDraft.originalTimes.length===frames.length?[...animationDraft.originalTimes]:times;animationDraft.hasTiming=gifInfo.length>0||animationDraft.originalTimes.length===frames.length;animationDraft.gifInfo=gifInfo;
 animationDraft.description=t(arrangement==='grid'&&!gifInfo.length?'web.dynamic.grid-ready':'web.dynamic.ready',{frames:frames.length,columns,rows,width:strip.width,height:strip.height})+(corrected?t('web.dynamic.corrected'):'');
 updateAnimationTiming();$('animation-upload').disabled=animationDraft.uploading;$('animation-download').disabled=false;$('animation-metadata').disabled=false;
}
for(const key of ['animation-files','animation-layout','animation-resolution','animation-arrangement','animation-columns','animation-rows','animation-fit']){
 const bind=()=>{
  $('animation-grid-settings').hidden=$('animation-arrangement').value!=='grid';
  const task=prepareAnimation(key==='animation-files'?$('animation-files').files:animationDraft.files,key==='animation-files'?null:animationDraft.originalTimes),generation=animationDraft.generation;
  task.catch(error=>{if(generation===animationDraft.generation){$('animation-analysis').textContent=error.message;toast(error.message);}});
 };
 document.addEventListener('DOMContentLoaded',()=>document.getElementById(key).addEventListener('change',bind));
}

document.addEventListener('DOMContentLoaded',()=>{
 $('animation-gif-timing').addEventListener('change',updateAnimationTiming);$('animation-ticks').addEventListener('change',updateAnimationTiming);$('animation-ticks').addEventListener('input',updateAnimationTiming);
 $('animation-metadata').addEventListener('click',()=>{if(animationDraft.blob)download(new Blob([JSON.stringify(CapeAnimation.metadata(animationDraft.frames[0].width,animationTimes()),null,2)],{type:'application/json'}),($('animation-id').value||'animated-cape')+'.png.mcmeta');});
 $('animation-download').addEventListener('click',()=>{if(animationDraft.blob)download(animationDraft.blob,($('animation-id').value||'animated-cape')+'.png');});
 $('animation-upload').addEventListener('click',async()=>{
  const id=$('animation-id').value.trim(),name=$('animation-name').value.trim(),ticks=Number($('animation-ticks').value);
  if(!/^[a-z0-9_-]{1,32}$/.test(id)||!name){toast(t('web.js.text-036'));return;}
  if(!Number.isInteger(ticks)||ticks<1||ticks>200){toast(t('web.dynamic.timing'));return;}
  if(!animationDraft.blob)return;
  const blob=animationDraft.blob,replace=$('animation-replace').checked,frameTimes=animationDraft.hasTiming&&$('animation-gif-timing').checked?animationTimes().join(','):null;
  animationDraft.uploading=true;$('animation-upload').disabled=true;
  const task=(async()=>api('/api/animation',{method:'POST',body:JSON.stringify({id,name,image:await base64(blob),replace,frameTicks:String(ticks),...(frameTimes?{frameTimes}: {})})}))();animationDraft.uploadTask=task.then(()=>undefined,()=>undefined);
  try{const result=await task;toast(result.message);$('animation-replace').checked=true;await loadLibrary();}
  catch(error){toast(error.message);}finally{animationDraft.uploading=false;$('animation-upload').disabled=!animationDraft.blob;}
 });
 const draw=time=>{
  const frames=animationDraft.frames;if(frames.length){const frame=frames[CapeAnimation.indexAt(time,animationTimes())],unit=frame.width/64,canvas=$('animation-preview'),ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.clearRect(0,0,canvas.width,canvas.height);ctx.drawImage(frame,unit,unit,10*unit,16*unit,0,0,canvas.width,canvas.height);}
  requestAnimationFrame(draw);
 };requestAnimationFrame(draw);
});
// Translation values are text only: never HTML or executable code.
let texts={};
function t(key,params={}){return String(texts[key]??key).replace(/\{([\w.-]+)}/g,(match,name)=>Object.hasOwn(params,name)?String(params[name]):match);}
async function loadTexts(){const response=await fetch('/api/texts',{credentials:'same-origin'});if(!response.ok)throw new Error('HTTP '+response.status);texts=await response.json();}
const $=id=>document.getElementById(id);
const state={csrf:'',source:null,draft:null,blob:null,view:'front',yaw:0,pitch:.06,busy:false,generation:0,editing:null};
const allowed=[64,128,256,512];
async function api(path,options={}){
 const headers={...options.headers};if(options.body){headers['Content-Type']='application/json';if(state.csrf)headers['X-Cape-CSRF']=state.csrf;}
 const response=await fetch(path,{credentials:'same-origin',...options,headers});
 const result=await response.json();if(!response.ok){if(response.status===401&&!path.endsWith('/login'))showLogin();throw new Error(result.error||t("web.js.text-004"));}return result;
}
function toast(message){$('toast').textContent=message;$('toast').hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('toast').hidden=true,3500);}
function showLogin(){state.csrf='';$('login-panel').hidden=false;$('workspace').hidden=true;$('logout').hidden=true;$('connection').textContent=t("web.js.text-005");}
async function enter(){
 await loadLibrary();$('login-panel').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;$('connection').textContent=t("web.js.text-006");
}
$('login-form').addEventListener('submit',async event=>{event.preventDefault();const button=event.currentTarget.querySelector('button');button.disabled=true;$('login-error').textContent='';try{const result=await api('/api/login',{method:'POST',body:JSON.stringify({code:$('login-code').value.trim()})});state.csrf=result.csrf;$('login-code').value='';await enter();}catch(error){$('login-error').textContent=error.message;}finally{button.disabled=false;}});
$('logout').addEventListener('click',async()=>{try{await api('/api/logout',{method:'POST',body:'{}'});}finally{showLogin();}});
async function openSession(){try{const s=await api('/api/session');state.csrf=s.csrf;await enter();}catch{showLogin();}}
function safeId(name){const id=name.replace(/\.[^.]+$/,'').toLowerCase().replace(/[^a-z0-9_-]+/g,'-').replace(/^-+|-+$/g,'').slice(0,32);return /^(con|prn|aux|nul|com[1-9]|lpt[1-9])$/.test(id)?'cape-'+id:id||'cape-'+Date.now().toString(36);}
function shortName(name){let result='';for(const ch of name){if(result.length+ch.length>20)break;result+=ch;}return result;}
async function sniff(file){
 const bytes=new Uint8Array(await file.slice(0,32).arrayBuffer());
 if(bytes.length>=8&&[137,80,78,71,13,10,26,10].every((v,i)=>bytes[i]===v)){
  const view=new DataView(bytes.buffer);if(view.getUint32(16)>8192||view.getUint32(20)>8192||view.getUint32(16)*view.getUint32(20)>16_777_216)throw new Error(t("web.js.text-007"));return 'PNG';
 }
 if(bytes[0]===255&&bytes[1]===216&&bytes[2]===255)return 'JPG';
 if(String.fromCharCode(...bytes.slice(0,4))==='RIFF'&&String.fromCharCode(...bytes.slice(8,12))==='WEBP')return 'WebP';
 throw new Error(t("web.js.text-008"));
}
async function choose(file,existing=null){
 if(!file)return;
 try{
  if(file.size>10*1024*1024)throw new Error(t("web.js.text-009"));
  const format=await sniff(file),bitmap=await createImageBitmap(file,{imageOrientation:'from-image'});
  if(bitmap.width>8192||bitmap.height>8192||bitmap.width*bitmap.height>16_777_216){bitmap.close();throw new Error(t("web.js.text-010"));}
  if(state.source)state.source.bitmap.close();state.source={bitmap,file,format,width:bitmap.width,height:bitmap.height};state.editing=existing;
  $('file-label').textContent=file.name;$('cape-id').value=existing?existing.id:safeId(file.name);$('cape-name').value=shortName(existing?existing.name:file.name.replace(/\.[^.]+$/,''));$('replace').checked=!!existing;
  $('layout').value='auto';$('resolution').value='auto';$('align-x').value='50';$('align-y').value='50';$('result').hidden=true;state.view='front';state.yaw=0;state.pitch=.06;await processImage();
 }catch(error){state.draft=null;state.blob=null;if(state.source)state.source.bitmap.close();state.source=null;for(const id of ['upload','repair','png-download'])$(id).disabled=true;$('analysis').textContent=error.message;$('analysis').className='analysis error';toast(error.message);}
}
$('dropzone').addEventListener('click',()=>$('file').click());$('file').addEventListener('change',event=>choose(event.target.files[0],state.editing&&$('replace').checked?state.editing:null));
for(const name of ['dragenter','dragover'])$('dropzone').addEventListener(name,event=>{event.preventDefault();$('dropzone').classList.add('dragover');});
for(const name of ['dragleave','drop'])$('dropzone').addEventListener(name,event=>{event.preventDefault();$('dropzone').classList.remove('dragover');});
$('dropzone').addEventListener('drop',event=>choose(event.dataTransfer.files[0],state.editing&&$('replace').checked?state.editing:null));
for(const name of ['layout','resolution','fit','sampling','trim','align-x','align-y'])$(name).addEventListener('input',()=>processImage().catch(error=>toast(error.message)));
function bounds(canvas){
 const {width:w,height:h}=canvas,ctx=canvas.getContext('2d'),data=ctx.getImageData(0,0,w,h).data;let x0=w,y0=h,x1=-1,y1=-1;
 for(let y=0;y<h;y++)for(let x=0;x<w;x++)if(data[(y*w+x)*4+3]){x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);}
 if(x1<0)throw new Error(t("web.js.text-011"));return {x:x0,y:y0,w:x1-x0+1,h:y1-y0+1};
}
function frontCanvas(source,rect,w,h,smooth){
 const out=document.createElement('canvas');out.width=w;out.height=h;const ctx=out.getContext('2d');ctx.imageSmoothingEnabled=smooth;
 const fit=$('fit').value,scale=fit==='contain'?Math.min(w/rect.w,h/rect.h):Math.max(w/rect.w,h/rect.h);
 const dw=rect.w*scale,dh=rect.h*scale,dx=(w-dw)*(Number($('align-x').value)/100),dy=(h-dh)*(Number($('align-y').value)/100);
 ctx.drawImage(source,rect.x,rect.y,rect.w,rect.h,dx,dy,dw,dh);return out;
}
function chooseWidth(layout,w,h){
 if($('resolution').value!=='auto')return Number($('resolution').value);
 const need=layout==='atlas'?w:Math.max(w/10,h/16)*64;return allowed.find(x=>x>=need)||512;
}
async function processImage(){
 if(!state.source)return;const generation=++state.generation,{bitmap,width,height,format}=state.source;
 state.draft=null;state.blob=null;for(const id of ['upload','repair','png-download'])$(id).disabled=true;
 const guessed=Math.abs(width/height-2)<.06?'atlas':'front';const layout=$('layout').value==='auto'?guessed:$('layout').value;
 const source=document.createElement('canvas');source.width=width;source.height=height;const sctx=source.getContext('2d',{willReadFrequently:true});sctx.drawImage(bitmap,0,0);
 let rect={x:0,y:0,w:width,h:height};if(layout==='front'&&$('trim').checked){if(!state.source.bounds)state.source.bounds=bounds(source);rect=state.source.bounds;}
 const outputWidth=chooseWidth(layout,rect.w,rect.h),scale=outputWidth/64,out=document.createElement('canvas');out.width=outputWidth;out.height=outputWidth/2;
 const ctx=out.getContext('2d');const smooth=$('sampling').value==='smooth';ctx.imageSmoothingEnabled=smooth;
 if(layout==='atlas')ctx.drawImage(source,0,0,width,height,0,0,out.width,out.height);
 else{
  const face=frontCanvas(source,rect,10*scale,16*scale,smooth);ctx.imageSmoothingEnabled=false;
  ctx.drawImage(face,scale,scale);ctx.drawImage(face,12*scale,scale);
  ctx.drawImage(face,0,0,1,face.height,0,scale,scale,16*scale);ctx.drawImage(face,face.width-1,0,1,face.height,11*scale,scale,scale,16*scale);
  ctx.drawImage(face,0,0,face.width,1,scale,0,10*scale,scale);ctx.drawImage(face,0,face.height-1,face.width,1,11*scale,0,10*scale,scale);
 }
 const cloth=ctx.getImageData(scale,scale,21*scale,16*scale).data;if(!cloth.some((v,i)=>i%4===3&&v>0))throw new Error(t("web.js.text-012"));
 const blob=await new Promise(resolve=>out.toBlob(resolve,'image/png'));if(generation!==state.generation)return;if(!blob)throw new Error(t("web.js.text-013"));
 state.draft=out;state.blob=blob;$('repair').disabled=state.busy;$('upload').disabled=state.busy;$('png-download').disabled=false;$('preview-empty').hidden=true;
 $('output-badge').textContent=t("web.dimensions",{width:out.width,height:out.height});$('output-info').textContent=t("web.js.text-001",{size:(blob.size/1024).toFixed(1),layout:layout==='front'?t("web.js.text-014"):t("web.js.text-015")});
 const messages=[t("web.js.text-002",{layout:layout==='front'?t("web.js.text-016"):t("web.js.text-017"),width,height,format})];
 const issues=[];if(format!=='PNG')issues.push(t("web.js.text-018"));if(layout==='front')issues.push(t("web.js.text-019"));if(layout==='atlas'&&(width!==outputWidth||height!==outputWidth/2))issues.push(t("web.js.text-020"));if(layout==='front'&&Math.abs(rect.w/rect.h-.625)>.01)issues.push($('fit').value==='cover'?t("web.js.text-021"):t("web.js.text-022"));
 $('analysis').textContent=messages[0]+(issues.length?t("web.issues",{issues:issues.join(t("web.issue-separator"))}):t("web.js.text-023"));$('analysis').className='analysis '+(issues.length?'issue':'okay');
 const a=$('atlas-preview');a.width=out.width;a.height=out.height;const actx=a.getContext('2d');actx.imageSmoothingEnabled=false;actx.clearRect(0,0,a.width,a.height);actx.drawImage(out,0,0);drawCape();
}
$('repair').addEventListener('click',async()=>{try{await processImage();toast(t("web.js.text-024"));}catch(error){toast(error.message);}});
function download(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),30000);}
$('png-download').addEventListener('click',()=>{if(state.blob)download(state.blob,($('cape-id').value||'cape')+'.png');});
$('pack-download').addEventListener('click',async()=>{try{const r=await fetch('/api/pack',{credentials:'same-origin'});if(!r.ok)throw new Error(t("web.js.text-025"));download(await r.blob(),'capes.zip');}catch(error){toast(error.message);}});
async function loadLibrary(){
 const [inventory,status]=await Promise.all([api('/api/capes'),api('/api/status')]);$('cape-count').textContent=inventory.capes.length;$('cape-grid').replaceChildren();$('library-empty').hidden=inventory.capes.length>0;
 for(const item of inventory.capes){
  const button=document.createElement('button');button.className='cape-card';const stage=document.createElement('div');stage.className='thumb-stage';const canvas=document.createElement('canvas');canvas.width=80;canvas.height=128;stage.append(canvas);
  const title=document.createElement('strong');title.textContent=item.name;const id=document.createElement('small');id.textContent=item.id;const stateLabel=document.createElement('span');stateLabel.className='state';stateLabel.textContent=item.enabled?(item.animated?t('web.dynamic.label'):item.image?t("web.js.text-026"):t("web.js.text-027")):t("web.js.text-028");button.append(stage,title,id,stateLabel);const wrapper=document.createElement('div');wrapper.className='cape-entry';wrapper.append(button);const remove=document.createElement('button');remove.type='button';remove.className='quiet delete-cape';remove.textContent=t('web.delete.button');remove.addEventListener('click',async()=>{if(!confirm(t('web.delete.confirm',{name:item.name})))return;remove.disabled=true;try{const result=await api('/api/delete',{method:'POST',body:JSON.stringify({id:item.id})});toast(result.message);await loadLibrary();}catch(error){toast(error.message);remove.disabled=false;}});wrapper.append(remove);$('cape-grid').append(wrapper);
  if(item.image){
   getImage(item).then(async file=>{if(!item.animated)await sniff(file);const bitmap=await createImageBitmap(file);const c=canvas.getContext('2d');c.imageSmoothingEnabled=false;if((item.animated||Math.abs(bitmap.width/bitmap.height-2)<.06)){const s=bitmap.width/64;c.drawImage(bitmap,s,s,10*s,16*s,0,0,80,128);}else c.drawImage(bitmap,0,0,bitmap.width,bitmap.height,0,0,80,128);bitmap.close();}).catch(()=>{stateLabel.textContent=t("web.js.text-029");});
  }else{const c=canvas.getContext('2d');c.fillStyle=item.id==='black'?'#202625':item.id==='white'?'#f9fbfa':'#93a89a';c.fillRect(0,0,80,128);}
  const visibility=document.createElement('label');visibility.className='check wardrobe-toggle';visibility.title=t('web.wardrobe.help');
  const toggle=document.createElement('input');toggle.type='checkbox';toggle.checked=item.wardrobeVisible!==false;
  const caption=document.createElement('span');caption.textContent=t('web.wardrobe.show');
  const visibilityState=document.createElement('small');visibilityState.className='fine wardrobe-state';
  const updateVisibility=()=>{visibilityState.textContent=t(toggle.checked?'web.wardrobe.public':'web.wardrobe.hidden');};updateVisibility();
  toggle.addEventListener('change',async()=>{const visible=toggle.checked;toggle.disabled=true;try{const result=await api('/api/wardrobe',{method:'POST',body:JSON.stringify({id:item.id,visible})});item.wardrobeVisible=result.wardrobeVisible;toggle.checked=result.wardrobeVisible;toast(result.message);}catch(error){toggle.checked=item.wardrobeVisible!==false;toast(error.message);}finally{toggle.disabled=false;updateVisibility();}});
  visibility.append(toggle,caption);wrapper.append(visibility,visibilityState);
  button.addEventListener('click',async()=>{if(item.animated){$('animation-layout').value='atlas';$('animation-arrangement').value='strip';$('animation-grid-settings').hidden=true;$('animation-id').value=item.id;$('animation-name').value=item.name;$('animation-ticks').value=item.frameTicks;$('animation-replace').checked=true;$('animation-panel').scrollIntoView({behavior:'smooth'});try{$('animation-gif-timing').checked=true;await prepareAnimation([await getImage(item)],item.frameTimes);}catch(error){toast(error.message);}return;}if(!item.image){state.editing=item;$('cape-id').value=item.id;$('cape-name').value=item.name;$('replace').checked=true;toast(t("web.js.text-030"));$('file').click();return;}try{await choose(await getImage(item),item);$('upload-title').scrollIntoView({behavior:'smooth',block:'start'});}catch(error){toast(error.message);}});
 }
 $('distribution').textContent=status.embeddedHost?t("web.js.text-031"):t("web.js.text-032");
}
async function getImage(item){const r=await fetch('/api/texture?id='+encodeURIComponent(item.id)+'&v='+item.modified,{credentials:'same-origin'});if(!r.ok)throw new Error(t("web.js.text-033"));return new File([await r.blob()],item.id+'.png');}
$('refresh').addEventListener('click',()=>loadLibrary().then(()=>toast(t("web.js.text-034"))).catch(error=>toast(error.message)));
async function base64(blob){return new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result.split(',')[1]);reader.onerror=()=>reject(new Error(t("web.js.text-035")));reader.readAsDataURL(blob);});}
$('upload').addEventListener('click',async()=>{
 if(state.busy||!state.source)return;const id=$('cape-id').value.trim(),name=$('cape-name').value.trim();if(!/^[a-z0-9_-]{1,32}$/.test(id)){toast(t("web.js.text-036"));$('cape-id').focus();return;}if(!name){toast(t("web.js.text-037"));$('cape-name').focus();return;}
 state.busy=true;$('upload').disabled=true;$('repair').disabled=true;$('upload').textContent=t("web.js.text-038");$('result').hidden=true;
 try{await processImage();$('upload').disabled=true;const result=await api('/api/install',{method:'POST',body:JSON.stringify({id,name,image:await base64(state.blob),replace:$('replace').checked})});
  const box=$('result');box.replaceChildren();box.className='result';const title=document.createElement('strong');title.textContent=t("web.success",{message:result.message});const info=document.createElement('div');info.textContent=t("web.js.text-003",{name:result.name,model:result.model,size:(result.packBytes/1024).toFixed(1)});const permission=document.createElement('code');permission.textContent=result.permission;const hint=document.createElement('div');hint.textContent=t("web.js.text-039");box.append(title,info,permission,hint);box.hidden=false;$('replace').checked=true;await loadLibrary();toast(t("web.js.text-040"));
 }catch(error){$('result').className='result failure';$('result').textContent=error.message;$('result').hidden=false;}
 finally{state.busy=false;$('upload').disabled=!state.draft;$('repair').disabled=!state.draft;$('upload').textContent=t("web.js.text-041");}
});
for(const button of document.querySelectorAll('[data-view]'))button.addEventListener('click',()=>{state.view=button.dataset.view;state.yaw=state.view==='back'?Math.PI:state.view==='angle'?.65:0;state.pitch=state.view==='angle'?.2:.06;document.querySelectorAll('[data-view]').forEach(b=>b.classList.toggle('selected',b===button));drawCape();});
let drag=null;const preview=$('cape-preview');preview.addEventListener('pointerdown',event=>{if(!state.draft)return;drag={x:event.clientX,y:event.clientY,yaw:state.yaw,pitch:state.pitch};preview.setPointerCapture(event.pointerId);});
preview.addEventListener('pointermove',event=>{if(!drag)return;state.yaw=drag.yaw+(event.clientX-drag.x)*.012;state.pitch=Math.max(-.7,Math.min(.7,drag.pitch+(event.clientY-drag.y)*.007));drawCape();});for(const event of ['pointerup','pointercancel'])preview.addEventListener(event,()=>{drag=null;});
function drawCape(){
 const canvas=$('cape-preview'),ctx=canvas.getContext('2d');ctx.clearRect(0,0,canvas.width,canvas.height);if(!state.draft)return;
 const scale=state.draft.width/64,w=.3125,t=.03125,faces=[
  {v:[[-w,0,t],[w,0,t],[w,-1,t],[-w,-1,t]],uv:[1,1,11,17]},
  {v:[[w,0,-t],[-w,0,-t],[-w,-1,-t],[w,-1,-t]],uv:[12,1,22,17]},
  {v:[[-w,0,-t],[-w,0,t],[-w,-1,t],[-w,-1,-t]],uv:[0,1,1,17]},
  {v:[[w,0,t],[w,0,-t],[w,-1,-t],[w,-1,t]],uv:[11,1,12,17]},
  {v:[[-w,0,-t],[w,0,-t],[w,0,t],[-w,0,t]],uv:[1,0,11,1]},
  {v:[[-w,-1,t],[w,-1,t],[w,-1,-t],[-w,-1,-t]],uv:[11,0,21,1]}
 ];
 const cy=Math.cos(state.yaw),sy=Math.sin(state.yaw),cx=Math.cos(state.pitch),sx=Math.sin(state.pitch);
 const project=p=>{const x=p[0]*cy+p[2]*sy,z=-p[0]*sy+p[2]*cy,y=(p[1]+.5)*cx-z*sx,depth=(p[1]+.5)*sx+z*cx;return {x:280+x*350,y:246-y*350,z:depth};};
 for(const face of faces){face.p=face.v.map(project);face.depth=face.p.reduce((s,p)=>s+p.z,0)/4;}faces.sort((a,b)=>a.depth-b.depth);
 ctx.save();ctx.fillStyle='#1d392017';ctx.beginPath();ctx.ellipse(280,452,93,12,0,0,Math.PI*2);ctx.fill();ctx.restore();
 for(const face of faces){const p=face.p,[u0,v0,u1,v1]=face.uv.map(v=>v*scale);ctx.save();ctx.beginPath();p.forEach((p,i)=>i?ctx.lineTo(p.x,p.y):ctx.moveTo(p.x,p.y));ctx.closePath();ctx.clip();ctx.imageSmoothingEnabled=false;ctx.transform((p[1].x-p[0].x)/(u1-u0),(p[1].y-p[0].y)/(u1-u0),(p[3].x-p[0].x)/(v1-v0),(p[3].y-p[0].y)/(v1-v0),p[0].x-u0*(p[1].x-p[0].x)/(u1-u0)-v0*(p[3].x-p[0].x)/(v1-v0),p[0].y-u0*(p[1].y-p[0].y)/(u1-u0)-v0*(p[3].y-p[0].y)/(v1-v0));ctx.drawImage(state.draft,0,0);ctx.restore();}
}
// Exposed only for local browser tests; it grants no server capability.
window.capeStudio={getDraft:()=>state.draft,getBlob:()=>state.blob,processImage};
loadTexts().then(openSession).catch(error=>{$("login-error").textContent=error.message;});
