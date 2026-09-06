"auto";

// SF2 Vision Farmer – AutoX fallback, calibrated from the uploaded 2340×1080 videos.
const C={joyX:.180,joyY:.768,joyDx:.067,joyDy:.108,punchX:.866,punchY:.710,kickX:.810,kickY:.835,
  roiL:.245,roiR:.785,roiT:.31,roiB:.865,far:.315,mid:.205,danger:.125};
if(!requestScreenCapture(true)){toast("Bildschirmaufnahme benötigt");exit();}
app.launchPackage("com.nekki.shadowfight2.paid");sleep(2500);
let running=true,px=null,ex=null,lastEx=null,lastT=Date.now(),combo=0,noFight=0;
events.observeKey();events.onKeyDown("volume_up",()=>{running=false;toast("Bot stoppt");});
function tapN(nx,ny,d){press(device.width*nx,device.height*ny,d||55)}
function move(dir,d){tapN(C.joyX+(dir>0?C.joyDx:-C.joyDx),C.joyY,d||150)}
function dash(dir){let x=device.width*(C.joyX+(dir>0?C.joyDx:-C.joyDx)),y=device.height*C.joyY;press(x,y,55);sleep(60);press(x,y,90)}
function punch(){tapN(C.punchX,C.punchY,55)} function kick(){tapN(C.kickX,C.kickY,55)}
function lowKick(){gestures([0,130,[device.width*C.joyX,device.height*(C.joyY+C.joyDy)]],[15,75,[device.width*C.kickX,device.height*C.kickY]])}
function fwdPunch(dir){gestures([0,145,[device.width*(C.joyX+(dir>0?C.joyDx:-C.joyDx)),device.height*C.joyY]],[18,75,[device.width*C.punchX,device.height*C.punchY]])}
function backPunch(dir){gestures([0,145,[device.width*(C.joyX+(dir>0?-C.joyDx:C.joyDx)),device.height*C.joyY]],[18,75,[device.width*C.punchX,device.height*C.punchY]])}
function combo2(){punch();sleep(135);punch()}
function redBars(img){let red=0;for(let y=img.height*.075;y<img.height*.18;y+=8)for(let x=img.width*.24;x<img.width*.76;x+=8){let c=images.pixel(img,x,y),r=colors.red(c),g=colors.green(c),b=colors.blue(c);if(r>125&&r>g*1.45&&r>b*1.45&&g<125)red++;}return red>10}
function fighters(img){
 let w=img.width,h=img.height,x0=w*C.roiL,x1=w*C.roiR,y0=h*C.roiT,y1=h*C.roiB,step=9;
 let hist=[];for(let x=x0;x<x1;x+=step){let n=0;for(let y=y0;y<y1;y+=step){let c=images.pixel(img,x,y),r=colors.red(c),g=colors.green(c),b=colors.blue(c),mx=Math.max(r,g,b),mn=Math.min(r,g,b),lum=(54*r+183*g+19*b)>>8;if(lum<48&&mx-mn<48)n++;}hist.push(n)}
 let sm=hist.map((v,i)=>{let s=0,n=0;for(let j=Math.max(0,i-2);j<=Math.min(hist.length-1,i+2);j++){s+=hist[j];n++}return s/n});
 let mean=sm.reduce((a,b)=>a+b,0)/sm.length,peaks=[];for(let i=2;i<sm.length-2;i++)if(sm[i]>Math.max(4.5,mean*1.55)&&sm[i]>=sm[i-1]&&sm[i]>=sm[i+1])peaks.push({i,v:sm[i]});peaks.sort((a,b)=>b.v-a.v);
 let chosen=[];for(let p of peaks){if(chosen.every(q=>Math.abs(q.i-p.i)>9)){chosen.push(p);if(chosen.length==2)break}}if(chosen.length<2)return null;
 let xs=chosen.map(p=>(x0+p.i*step)/w).sort((a,b)=>a-b);return xs;
}
while(running){let img=captureScreen();if(!redBars(img)){noFight++;img.recycle();if(noFight>35){tapN(.50,.82,55);noFight=0;sleep(900)}else sleep(180);continue;}noFight=0;let fs=fighters(img);img.recycle();if(!fs){lowKick();sleep(320);continue;}
 if(px==null){px=fs[0];ex=fs[1]}else{let c1=Math.abs(fs[0]-px)+Math.abs(fs[1]-ex),c2=Math.abs(fs[1]-px)+Math.abs(fs[0]-ex);let np,ne;if(c1<=c2){np=fs[0];ne=fs[1]}else{np=fs[1];ne=fs[0]}px=.62*px+.38*np;ex=.62*ex+.38*ne;}
 let now=Date.now(),dt=Math.max(1,now-lastT),vel=lastEx==null?0:(ex-lastEx)*1000/dt,lastEx=ex,lastT=now,dir=ex>px?1:-1,closing=dir>0?-vel:vel,dist=Math.abs(ex-px);
 if(dist>C.far){dash(dir);sleep(280)}else if(dist>C.mid){fwdPunch(dir);sleep(340)}else if(dist<.18&&closing>.12){sleep(210);combo2();sleep(360)}else if(dist<C.danger){if(combo++%3==0)backPunch(dir);else lowKick();sleep(350)}else{if(combo++%4==0){punch();sleep(135);kick()}else combo2();sleep(350)}
}
