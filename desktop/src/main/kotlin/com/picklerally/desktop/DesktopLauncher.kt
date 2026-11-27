package com.picklerally.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.picklerally.core.PickleRallyGame

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Pickle Rally — Phase 1")
        setWindowedMode(450, 900)
        setResizable(true)
        useVsync(true)
        setForegroundFPS(60)
    }

    Lwjgl3Application(PickleRallyGame(FakeLocalNetworkService()), config)
}
