package se.kodexa.screwbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executor
import kotlin.math.hypot

/** Experimental closed-loop controller. Never taps when the screen is uncertain. */
class BotService : AccessibilityService() {
 private val handler = Handler(Looper.getMainLooper())
 private val executor = Executor { task -> handler.post(task) }
 private var running = false
 private var busy = false
 private var moves = 0
 private var scans = 0
 private var victories = 0
 private var victoryFrames = 0
 private var waitingForLevel = false
 private var waitScans = 0
 private var lastTarget: ScrewDetector.Target? = null
 private val planner=MovePlanner()
 private var lastBin=-1
 private var lastTargetFailures = 0
 private var rotations = 0
 // Screen-space swipe directions. Alternate axes and revisit opposite viewpoints.
 private val viewSearch = listOf(
  Pair(-0.30f, 0f), Pair(0.30f, 0f),
  Pair(0f, -0.24f), Pair(0f, 0.24f),
  Pair(-0.24f, -0.18f), Pair(0.24f, -0.18f),
  Pair(-0.24f, 0.18f), Pair(0.24f, 0.18f),
  Pair(-0.40f, 0f), Pair(0.40f, 0f),
  Pair(0f, -0.36f), Pair(0f, 0.36f)
 )
 private var calibrationFailures = 0
 private var lastSuccessful = false
 private var nextDelay = 350L
 private var lastTargets: List<ScrewDetector.Target> = emptyList()
 private var overlay: View? = null
 private var status: TextView? = null
 private var expanded = true
 private var overlayParams: WindowManager.LayoutParams? = null
 private var controls: LinearLayout? = null
 private var toggleButton: Button? = null

 override fun onServiceConnected() { super.onServiceConnected(); showControls(); startBot() }
 override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
 override fun onInterrupt() { stop("Tjänsten avbruten") }
 override fun onDestroy() {
  stop("Stoppad")
  overlay?.let { try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: IllegalArgumentException) { } }
  super.onDestroy()
 }
 private fun showControls() {
  val wm = getSystemService(WINDOW_SERVICE) as WindowManager
  val density = resources.displayMetrics.density
  val bar = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setBackgroundColor(0xE5222222.toInt())
   setPadding(6, 6, 6, 6)
  }
  val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  val handle = TextView(this).apply {
   text = "☰ FLYTTA"
   setTextColor(-1)
   textSize = 14f
   setPadding(12, 12, 16, 12)
  }
  // Drag from the handle; buttons remain clickable and never start a drag.
  handle.setOnTouchListener(object : View.OnTouchListener {
   var downX = 0f; var downY = 0f
   var startX = 0; var startY = 0
   override fun onTouch(v: View, event: MotionEvent): Boolean {
    val params = overlayParams ?: return false
    when (event.actionMasked) {
     MotionEvent.ACTION_DOWN -> {
      downX = event.rawX; downY = event.rawY
      startX = params.x; startY = params.y
      return true
     }
     MotionEvent.ACTION_MOVE -> {
      val dx = (event.rawX - downX).toInt()
      val dy = (event.rawY - downY).toInt()
      val screen = resources.displayMetrics
      params.x = (startX + dx).coerceIn(0, (screen.widthPixels - bar.width).coerceAtLeast(0))
      params.y = (startY + dy).coerceIn(0, (screen.heightPixels - bar.height).coerceAtLeast(0))
      try { wm.updateViewLayout(bar, params) } catch (_: IllegalArgumentException) { }
      return true
     }
     MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
    }
    return false
   }
  })
  header.addView(handle)
  toggleButton = Button(this).apply {
   text = "−"
   setOnClickListener {
    expanded = !expanded
    controls?.visibility = if (expanded) View.VISIBLE else View.GONE
    text = if (expanded) "−" else "+"
    // Keep the floating window inside the display after resizing.
    overlayParams?.let { params ->
     params.x = params.x.coerceAtLeast(0)
     params.y = params.y.coerceAtLeast(0)
     wm.updateViewLayout(bar, params)
    }
   }
  }
  header.addView(toggleButton, LinearLayout.LayoutParams((58*density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
  bar.addView(header)
  val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
  val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  buttons.addView(Button(this).apply { text="START"; setOnClickListener { startBot() } })
  buttons.addView(Button(this).apply { text="STOPP"; setOnClickListener { stop("Stoppad") } })
  actions.addView(buttons)
  status = TextView(this).apply { text="Redo"; setTextColor(-1); setPadding(10, 8, 10, 8); maxWidth = (250*density).toInt() }
  actions.addView(status)
  actions.addView(Button(this).apply {
   text="AVSLUTA"
   setOnClickListener { exitBot() }
  })
  controls = actions
  bar.addView(actions)
  val params = WindowManager.LayoutParams(
   WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
   PixelFormat.TRANSLUCENT
  ).apply { gravity=Gravity.TOP or Gravity.START; x=12; y=(350*density).toInt() }
  overlayParams=params
  overlay=bar
  wm.addView(bar,params)
 }
 private fun exitBot() {
  stop("Avslutad")
  handler.removeCallbacksAndMessages(null)
  overlay?.let {
   try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
   catch (_: IllegalArgumentException) { }
  }
  overlay=null; controls=null; status=null; overlayParams=null
  // Disable the accessibility service itself: no overlay or background automation remains.
  disableSelf()
 }
 private fun startBot() {
  if(running) return
  running=true; busy=false; moves=0; scans=0; rotations=0; victories=0; victoryFrames=0
  waitingForLevel=false; waitScans=0; lastTarget=null; lastTargetFailures=0; lastTargets=emptyList(); planner.reset(); lastBin=-1; calibrationFailures=0; lastSuccessful=false; nextDelay=350L
  status?.text="Analyserar"
  schedule(300)
 }
 private fun stop(message:String) { running=false; handler.removeCallbacksAndMessages(null); status?.text=message }
 private fun schedule(delay:Long) { if(running) handler.postDelayed({ tick() },delay) }
 private fun tick() {
  if(!running || busy) return
  if(++scans>600 || moves>180) { stop("Säkerhetsstopp: för många försök"); return }
  busy=true
  try {
   takeScreenshot(android.view.Display.DEFAULT_DISPLAY,executor,object:TakeScreenshotCallback {
    override fun onSuccess(result:ScreenshotResult) {
     val buffer=result.hardwareBuffer
     var bitmap:Bitmap?=null
     try {
      val wrapped=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)
      bitmap=wrapped?.copy(Bitmap.Config.ARGB_8888,false)
      wrapped?.recycle()
      val b=bitmap ?: run { stop("Kan inte läsa skärm"); return }
      if (running) process(b)
     } catch(e:Exception) { stop("Analysfel: ${e.javaClass.simpleName}") }
     finally { bitmap?.recycle(); buffer.close(); busy=false }
    }
    override fun onFailure(errorCode:Int) { busy=false; stop("Skärmbild misslyckades: $errorCode") }
   })
  } catch(e:Exception) { busy=false; stop("Skärmbild stöds inte: ${e.javaClass.simpleName}") }
 }
 private fun process(b:Bitmap) {
  val state=ScreenState.classify(b)
  if(state==ScreenState.Kind.VICTORY) {
   victoryFrames++
   if(waitingForLevel) { status?.text="Väntar på ny nivå"; schedule(800); return }
   if(victoryFrames<3) { schedule(450); return }
   waitingForLevel=true; waitScans=0; status?.text="Klar – Next"
   gesture(b.width*0.286f,b.height*0.684f) { ok -> if(!ok) stop("Next misslyckades") else schedule(1700) }
   return
  }
  victoryFrames=0
  if(waitingForLevel) {
   if(state==ScreenState.Kind.PUZZLE) {
    waitingForLevel=false; moves=0; rotations=0; lastTarget=null; lastTargets=emptyList(); lastTargetFailures=0; planner.reset(); lastBin=-1; calibrationFailures=0; victories++
    status?.text="Ny nivå – analyserar"; schedule(650)
   } else {
    if(++waitScans>15) stop("Nästa nivå kunde inte bekräftas") else schedule(1100)
   }
   return
  }
  if(state!=ScreenState.Kind.PUZZLE) { status?.text="Väntar på spel"; schedule(850); return }
  val targets=ScrewDetector.detect(b)
  // Require visible changes before treating a tap as successful.
  val old=lastTarget
  if(old!=null) {
   val stillVisible=targets.any { Palette.hueDistance(it.hue,old.hue)<12f && hypot((it.x-old.x).toDouble(),(it.y-old.y).toDouble())<24.0 }
   if(stillVisible) { lastTargetFailures++; lastSuccessful=false; nextDelay=(nextDelay+90L).coerceAtMost(800L) } else { lastTargetFailures=0; rotations=0; moves++; lastSuccessful=true; nextDelay=(nextDelay-35L).coerceAtLeast(180L) }
   planner.report(old,lastBin,!stillVisible)
   lastTarget=null
  }
  if(lastTargetFailures>=3) { lastTargetFailures=0; status?.text="Misslyckat drag – söker ny vinkel" }
  val bins=Palette.activeBins(b)
  if(bins.size!=2) {
   calibrationFailures++
   status?.text="Kalibrering ${bins.size}/2 – försök $calibrationFailures"
   if(calibrationFailures>=30) stop("Kalibrering misslyckades – kontrollera skärmen")
   else schedule(650)
   return
  }
  calibrationFailures=0
  planner.observeBins(bins)
  val decision=planner.choose(targets,bins,b.width,b.height)
  val candidate=decision?.target
  lastTargets=targets
  if(candidate!=null && lastTargetFailures==0) {
   lastTarget=candidate; lastBin=decision!!.bin; status?.text="Drag ${moves+1} • ${planner.stats()} • ${nextDelay}ms"
   gesture(candidate.x,candidate.y) { ok -> if(!ok) stop("Tryck misslyckades") else schedule(nextDelay) }
  } else if(rotations < viewSearch.size) {
   val direction = viewSearch[rotations]
   rotations++
   val cx = b.width * 0.50f
   val cy = b.height * 0.54f
   // Keep swipe endpoints inside the central puzzle, away from top bins and bottom boosters.
   val x1 = cx - b.width * direction.first / 2f
   val y1 = cy - b.height * direction.second / 2f
   val x2 = cx + b.width * direction.first / 2f
   val y2 = cy + b.height * direction.second / 2f
   status?.text = "Söker vinkel $rotations/${viewSearch.size}"
   swipe(x1,y1,x2,y2) { ok ->
    if(!ok) stop("Rotation misslyckades") else {
     lastTargetFailures=0; lastTargets=emptyList(); schedule(500)
    }
   }
  } else stop("Inga säkra drag – behöver bättre spelmodell")
 }
 private fun gesture(x:Float,y:Float,done:(Boolean)->Unit) = dispatchPath(Path().apply { moveTo(x,y) },65,done)
 private fun swipe(x1:Float,y1:Float,x2:Float,y2:Float,done:(Boolean)->Unit) = dispatchPath(Path().apply { moveTo(x1,y1); lineTo(x2,y2) },310,done)
 private fun dispatchPath(path:Path,duration:Long,done:(Boolean)->Unit) {
  if (!running) return
  val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,duration)).build()
  val ok=dispatchGesture(gesture,object:GestureResultCallback() {
   override fun onCompleted(gestureDescription:GestureDescription?) { if (running) done(true) }
   override fun onCancelled(gestureDescription:GestureDescription?) { if (running) done(false) }
  },handler)
  if(!ok) done(false)
 }
}
