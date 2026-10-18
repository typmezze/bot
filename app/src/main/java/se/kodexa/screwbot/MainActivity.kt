package se.kodexa.screwbot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(35,65,35,35) }
  layout.addView(TextView(this).apply { text = "Screw Out AutoBot – prototyp\n\n1. Aktivera tillgänglighetstjänsten.\n2. Öppna spelet.\n3. Använd START i den flytande kontrollen.\n\nDra panelen med ☰ FLYTTA. Använd +/− för att minimera/expandera och AVSLUTA för att stänga av botens tillgänglighetstjänst.\n\nTillåt bara för privat testning."; textSize = 18f })
  layout.addView(Button(this).apply { text = "Öppna tillgänglighetsinställningar"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
  setContentView(layout)
 }
}
