package se.kodexa.screwbot

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/** Hue segmentation independent of brightness; candidates are unverified screw-like blobs. */
object ScrewDetector {
 data class Target(val x:Float,val y:Float,val color:String,val confidence:Double,val hue:Float)
 fun detect(bitmap:Bitmap):List<Target> {
  val w=bitmap.width; val h=bitmap.height
  val step=max(2,min(w,h)/260); val sw=w/step; val sh=h/step
  val labels=IntArray(sw*sh); val hsv=FloatArray(3)
  for(y in 0 until sh) for(x in 0 until sw) {
   val ny=y*step.toFloat()/h
   if(ny !in 0.37f..0.82f) continue
   Color.colorToHSV(bitmap.getPixel(x*step,y*step),hsv)
   if(hsv[1]<0.38f || hsv[2]<0.28f) continue
   // 10-degree bins, not fixed color names. Light/dark share a hue class.
   labels[y*sw+x]=1+(hsv[0]/10f).toInt().coerceIn(0,35)
  }
  val seen=BooleanArray(labels.size); val result=mutableListOf<Target>()
  for(idx in labels.indices) {
   val c=labels[idx]; if(c==0 || seen[idx]) continue
   val queue=IntArray(labels.size); var head=0; var tail=1; queue[0]=idx; seen[idx]=true
   var sx=0L; var sy=0L; var minx=sw; var maxx=0; var miny=sh; var maxy=0
   while(head<tail) {
    val p=queue[head++]; val px=p%sw; val py=p/sw
    sx+=px; sy+=py; minx=min(minx,px); maxx=max(maxx,px); miny=min(miny,py); maxy=max(maxy,py)
    val ns=intArrayOf(if(px>0)p-1 else -1,if(px<sw-1)p+1 else -1,if(py>0)p-sw else -1,if(py<sh-1)p+sw else -1)
    for(n in ns) if(n>=0 && !seen[n] && labels[n]==c) { seen[n]=true; queue[tail++]=n }
   }
   val area=tail; val bw=maxx-minx+1; val bh=maxy-miny+1; val ratio=bw.toDouble()/max(1,bh)
   if(area in 7..180 && ratio in 0.55..1.8 && area.toDouble()/(bw*bh) in 0.35..0.95) {
    val x=(sx.toFloat()/area+0.5f)*step; val y=(sy.toFloat()/area+0.5f)*step
    val sampled=Palette.sample(bitmap,x/w,y/h,3)
    if(sampled!=null) result.add(Target(x,y,"hue_${(sampled.hue/10).toInt()}",0.55,sampled.hue))
   }
  }
  return result.sortedWith(compareBy<Target>{it.y}.thenBy{it.x})
 }
}
