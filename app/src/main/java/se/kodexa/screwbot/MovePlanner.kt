package se.kodexa.screwbot

import kotlin.math.abs
import kotlin.math.hypot

/** Conservative, observable heuristic, NOT a solved 3D physics/occlusion model.
 * Avoids repeated failed targets, tracks per-bin occupancy between observations,
 * and favors isolated visible screws matching the currently active colors.
 */
class MovePlanner {
 data class Decision(val target:ScrewDetector.Target, val bin:Int, val score:Double)
 private val failures=mutableMapOf<String,Int>()
 private val occupied=intArrayOf(0,0)
 private val observations=mutableMapOf<String,Int>()
 private var confirmedMoves=0
 private var rejectedMoves=0
 private var previousBins:List<Palette.Bin> = emptyList()
 private fun key(t:ScrewDetector.Target)="${(t.x/22).toInt()}:${(t.y/22).toInt()}:${(t.hue/12).toInt()}"
 fun reset() { failures.clear(); observations.clear(); occupied.fill(0); previousBins=emptyList(); confirmedMoves=0; rejectedMoves=0 }
 fun stats():String = "OK $confirmedMoves / fel $rejectedMoves"
 fun observeBins(bins:List<Palette.Bin>) {
  if(bins.size!=2) return
  if(previousBins.size==2) for(i in 0..1) {
   if(Palette.hueDistance(previousBins[i].sample.hue,bins[i].sample.hue)>18f) occupied[i]=0
  }
  previousBins=bins
 }
 fun report(t:ScrewDetector.Target, bin:Int, removed:Boolean) {
  if(removed) { confirmedMoves++; failures.remove(key(t)); if(bin in 0..1) occupied[bin]=(occupied[bin]+1).coerceAtMost(3) }
  else { rejectedMoves++; failures[key(t)]=(failures[key(t)]?:0)+1 }
 }
 fun choose(targets:List<ScrewDetector.Target>,bins:List<Palette.Bin>,width:Int,height:Int):Decision? {
  // Prefer stable targets visible in at least two consecutive screenshots.
  val present=targets.map { key(it) }.toSet()
  observations.keys.retainAll(present)
  targets.forEach { observations[key(it)]=(observations[key(it)]?:0).plus(1).coerceAtMost(5) }
  if(bins.size!=2) return null
  val candidates=mutableListOf<Decision>()
  for(t in targets) {
   if(t.confidence<0.50 || failures.getOrDefault(key(t),0)>=2 || (observations[key(t)]?:0)<2) continue
   for(bin in bins) {
    if(bin.id !in 0..1 || occupied[bin.id]>=3) continue
    val d=Palette.hueDistance(t.hue,bin.sample.hue)
    if(d>12f) continue
    val neighbors=targets.count { other -> other!==t && hypot((other.x-t.x).toDouble(),(other.y-t.y).toDouble())<width*0.07 }
    val centrality=abs(t.x-width/2f)/width
    val score=100.0-d*3.0-neighbors*9.0-failures.getOrDefault(key(t),0)*40.0+centrality*6.0+(observations[key(t)]?:0)*2.0
    candidates.add(Decision(t,bin.id,score))
   }
  }
  return candidates.maxByOrNull { it.score }
 }
}
