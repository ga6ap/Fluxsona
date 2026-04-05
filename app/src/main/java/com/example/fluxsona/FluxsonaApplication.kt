package com.example.fluxsona

import android.app.Application
import com.example.fluxsona.di.AppContainer

class FluxsonaApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
