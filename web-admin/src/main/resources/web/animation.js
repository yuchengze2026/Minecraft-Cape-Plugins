'use strict';
(function(root){
 function fail(key){throw new Error(key);}
 function plan(width,height,{arrangement='strip',layout='auto',columns=8,rows=8}={}){
  if(!Number.isInteger(width)||!Number.isInteger(height)||width<1||height<1||width>8192||height>16384||width*height>8388608)fail('web.dynamic.limit');
  if(!['strip','grid'].includes(arrangement)||!['auto','front','atlas'].includes(layout))fail('web.dynamic.dimensions');
  const rects=[];let selected=layout;
  if(arrangement==='grid'){
   if(!Number.isInteger(columns)||!Number.isInteger(rows)||columns<1||rows<1||columns*rows>64||columns>width||rows>height)fail('web.dynamic.grid-invalid');
   if(selected==='auto'){
    const ratio=(width/columns)/(height/rows);
    if(Math.abs(ratio/.625-1)<.1)selected='front';
    else if(Math.abs(ratio/2-1)<.1)selected='atlas';
    else fail('web.dynamic.grid-format');
   }
   // Round boundaries, not cell sizes: every source pixel belongs to exactly one frame.
   for(let row=0;row<rows;row++)for(let col=0;col<columns;col++){
    const x=Math.round(col*width/columns),y=Math.round(row*height/rows);
    rects.push({x,y,w:Math.round((col+1)*width/columns)-x,h:Math.round((row+1)*height/rows)-y});
   }
  }else{
   const valid=h=>Number.isInteger(h)&&h>0&&height%h===0&&height/h<=64;
   if(selected==='auto'){
    const atlas=valid(width/2),front=valid(width*8/5);
    if(atlas&&front)fail('web.dynamic.ambiguous');
    selected=atlas?'atlas':front?'front':fail('web.dynamic.dimensions');
   }
   const h=selected==='front'?width*8/5:width/2;
   if(!valid(h))fail('web.dynamic.dimensions');
   for(let y=0;y<height;y+=h)rects.push({x:0,y,w:width,h});
  }
  return {layout:selected,rects,adjusted:rects.some(r=>selected==='front'?r.w*8!==r.h*5:r.w!==r.h*2)};
 }
 function fit(rect,width,height,mode='contain'){
  if(!['contain','cover'].includes(mode))fail('web.dynamic.dimensions');
  const result={sx:rect.x,sy:rect.y,sw:rect.w,sh:rect.h,dx:0,dy:0,dw:width,dh:height};
  const ratio=width/height;
  if(mode==='contain'){
   const scale=Math.min(width/rect.w,height/rect.h);result.dw=rect.w*scale;result.dh=rect.h*scale;result.dx=(width-result.dw)/2;result.dy=(height-result.dh)/2;
  }else if(rect.w/rect.h>ratio){result.sw=rect.h*ratio;result.sx+=(rect.w-result.sw)/2;}
  else {result.sh=rect.w/ratio;result.sy+=(rect.h-result.sh)/2;}
  return result;
 }
 function frame(bitmap,rect,layout,width,mode='contain'){
  const atlas=document.createElement('canvas');atlas.width=width;atlas.height=width/2;
  const ctx=atlas.getContext('2d');ctx.imageSmoothingEnabled=false;
  if(layout==='atlas'){ctx.drawImage(bitmap,rect.x,rect.y,rect.w,rect.h,0,0,width,width/2);return atlas;}
  const unit=width/64,face=document.createElement('canvas');face.width=unit*10;face.height=unit*16;
  const fc=face.getContext('2d');fc.imageSmoothingEnabled=false;
  const p=fit(rect,face.width,face.height,mode);fc.drawImage(bitmap,p.sx,p.sy,p.sw,p.sh,p.dx,p.dy,p.dw,p.dh);
  ctx.drawImage(face,unit,unit);ctx.drawImage(face,12*unit,unit);
  ctx.drawImage(face,0,0,1,face.height,0,unit,unit,16*unit);ctx.drawImage(face,face.width-1,0,1,face.height,11*unit,unit,unit,16*unit);
  ctx.drawImage(face,0,0,face.width,1,unit,0,10*unit,unit);ctx.drawImage(face,0,face.height-1,face.width,1,11*unit,0,10*unit,unit);
  return atlas;
 }
 function currentTimes(count,ticks,original,keep){return keep&&original.length===count?original:Array(count).fill(ticks);}
 function indexAt(time,times){const total=times.reduce((a,b)=>a+b,0)*50;let phase=((time%total)+total)%total;for(let i=0;i<times.length;i++){phase-=times[i]*50;if(phase<0)return i;}return times.length-1;}
 function metadata(width,times){return {animation:{width,height:width/2,frametime:times[0],interpolate:false,frames:times.map((time,index)=>({index,time}))}};}
 function serialQueue(){let previous=Promise.resolve();return task=>{const result=previous.then(task,task);previous=result.then(()=>undefined,()=>undefined);return result;};}
 const api={plan,fit,frame,currentTimes,indexAt,metadata,serialQueue};if(typeof module==='object'&&module.exports)module.exports=api;else root.CapeAnimation=api;
})(typeof globalThis==='object'?globalThis:this);
