package com.eaglelan.app

import android.app.Application

class EagleApp : Application() {
    val services: AppServices by lazy { createAndroidServices(this) }
}
