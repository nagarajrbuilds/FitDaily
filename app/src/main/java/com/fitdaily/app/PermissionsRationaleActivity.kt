package com.fitdaily.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(246,248,251))
        }
        box.addView(TextView(this).apply {
            text = "FitDaily & Health Connect"
            textSize = 26f
            setTextColor(Color.rgb(23,37,58))
        })
        box.addView(TextView(this).apply {
            text = "\nFitDaily reads only the Health Connect data you approve: steps, sleep, exercise sessions and weight.\n\nThis data is used to update your personal FitDaily dashboard. Manual FitDaily records remain stored on your device. You can change Health Connect access at any time in Android settings."
            textSize = 16f
            setTextColor(Color.rgb(82,101,123))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        setContentView(box)
    }
}