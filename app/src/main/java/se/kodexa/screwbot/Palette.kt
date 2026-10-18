package se.kodexa.screwbot

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min

/** Learn active bin colors from the current frame; do not assume a fixed list of hues. */
object Palette {
 data class Sample(val hue:Float, val saturation:Float, val brightness:Float)
 data class Bin(val id:Int, val sample:Sample)
 fun hueDistance(a:Float,b:Float):Float { val d=abs(a-b)%360f; return min(d,360f-d) }
 fun sample(b:Bitmap, nx:Float, ny:Float, radius:Int=6):Sample? {
  val cx=(nx*b.width).toInt(); val cy=(ny*b.height).toInt()
  var xx=0.0; var yy=0.0; var s=0.0; var v=0.0; var n=0
  val hsv=FloatArray(3)
  for(y in (cy-radius).coerceAtLeast(0)..(cy+radius).coerceAtMost(b.height-1) step 2)
   for(x in (cx-radius).coerceAtLeast(0)..(cx+radius).coerceAtMost(b.width-1) step 2) {
    Color.colorToHSV(b.getPixel(x,y),hsv)
    if(hsv[1]<0.30f || hsv[2]<0.22f) continue
    val rad=Math.toRadians(hsv[0].toDouble()); xx+=kotlin.math.cos(rad); yy+=kotlin.math.sin(rad)
    s+=hsv[1]; v+=hsv[2]; n++
   }
  if(n<4) return null
  val hue=((Math.toDegrees(kotlin.math.atan2(yy,xx))+360.0)%360.0).toFloat()
  return Sample(hue,(s/n).toFloat(),(v/n).toFloat())
 }
 /** Multiple sampling points on the colored plastic rim, not the pale bin interior.
  * Coordinates are normalized to the 709x1536 reference screenshot. */
 fun activeBins(b:Bitmap):List<Bin> {
  val positions=listOf(
   listOf(0.14f to 0.113f, 0.075f to 0.133f, 0.205f to 0.133f,
          0.063f to 0.169f, 0.214f to 0.169f, 0.14f to 0.186f),
   listOf(0.38f to 0.113f, 0.315f to 0.133f, 0.445f to 0.133f,
          0.303f to 0.169f, 0.455f to 0.169f, 0.38f to 0.186f)
  )
  return positions.mapIndexedNotNull { id, pts ->
   val samples=pts.mapNotNull { (x,y) -> sample(b,x,y,4) }
   // Require agreement between at least two independently sampled rim regions.
   val cluster=samples.map { center ->
    samples.filter { hueDistance(it.hue,center.hue)<17f }
   }.maxByOrNull { it.size }
   if(cluster==null || cluster.size<2) null else {
    val angles=cluster.map { Math.toRadians(it.hue.toDouble()) }
    val hue=((Math.toDegrees(kotlin.math.atan2(angles.sumOf { kotlin.math.sin(it) },
     angles.sumOf { kotlin.math.cos(it) }))+360.0)%360.0).toFloat()
    Bin(id,Sample(hue,cluster.map { it.saturation }.average().toFloat(),
      cluster.map { it.brightness }.average().toFloat()))
   }
  }
 }
 fun matches(a:Sample,b:Sample):Boolean = hueDistance(a.hue,b.hue)<=14f &&
   abs(a.saturation-b.saturation)<=0.55f // tolerate shadow/highlight; keep cyan separate from blue
}
