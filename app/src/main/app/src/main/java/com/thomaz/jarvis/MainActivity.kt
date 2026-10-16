package com.thomaz.jarvis

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = TextView(this)
        t.text = "J.A.R.V.I.S.\nSistema online"
        t.setTextColor(Color.CYAN)
        t.textSize = 28f
        t.gravity = Gravity.CENTER
        t.setBackgroundColor(Color.BLACK)
        setContentView(t)
    }
}
