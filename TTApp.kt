
package com.ttpredictor

import android.app.Application

class TTApp: Application() {
    override fun onCreate() {
        super.onCreate()
        MyApp.context = applicationContext
    }
}
