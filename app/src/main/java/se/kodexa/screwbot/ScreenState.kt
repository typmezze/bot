package se.kodexa.screwbot

import android.graphics.Bitmap
import android.graphics.Color

/** Calibrated for the supplied 709x1536 victory screenshot; coordinates are normalized. */
object ScreenState {
 enum class Kind { PUZZLE, VICTORY, UNKNOWN }
 private fun ratio(b:Bitmap, x0:Double,y0:Double,x1:Double,y1:Double, predicate:(FloatArray)->Boolean):Double {
  val hsv=FloatArray(3); var count=0; var matched=0
  val xa=(b.width*x0).toInt().coerceIn(0,b.width-1)
  val xb=(b.width*x1).toInt().coerceIn(xa+1,b.width)
  val ya=(b.height*y0).toInt().coerceIn(0,b.height-1)
  val yb=(b.height*y1).toInt().coerceIn(ya+1,b.height)
  for(y in ya until yb step 5) for(x in xa until xb step 5) {
   Color.colorToHSV(b.getPixel(x,y),hsv)
   if(predicate(hsv)) matched++
   count++
  }
  return if(count==0) 0.0 else matched.toDouble()/count
 }
 fun classify(b:Bitmap):Kind {
  // Victory: darkened puzzle, yellow title, green left NEXT, cyan right reward.
  val dark=ratio(b,0.05,0.13,0.92,0.34){it[2]<0.30f}
  val title=ratio(b,0.20,0.21,0.82,0.31){it[0] in 30f..65f && it[1]>0.55f && it[2]>0.65f}
  val next=ratio(b,0.12,0.66,0.46,0.70){it[0] in 75f..145f && it[1]>0.48f && it[2]>0.35f}
  val reward=ratio(b,0.56,0.66,0.87,0.70){it[0] in 170f..210f && it[1]>0.35f && it[2]>0.45f}
  if(dark>0.72 && title>0.08 && next>0.45 && reward>0.25) return Kind.VICTORY
  val wood=ratio(b,0.08,0.39,0.94,0.80){it[0] in 18f..46f && it[1] in 0.25f..0.85f && it[2]>0.40f}
  return if(wood>0.10) Kind.PUZZLE else Kind.UNKNOWN
 }
}
