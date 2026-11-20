package com.findbeam.app.core.store

import android.content.Context

/** Minimal service locator for app-scoped singletons. */
object Locator {

    lateinit var appContext: Context
        private set

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
    val spotStore: SpotStore by lazy { SpotStore(appContext) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
