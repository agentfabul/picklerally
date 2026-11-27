package com.picklerally.core.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import com.picklerally.core.PickleRallyGame
import com.picklerally.core.court.CourtColors
import com.picklerally.core.net.NearbyDiscovery
import com.picklerally.core.net.NearbyHost
import ktx.app.KtxScreen
import ktx.graphics.use

/**
 * Nearby (offline) 1v1 lobby: host a game for players next to you, or pick one from the
 * live list of nearby hosts. Works over Wi-Fi, a phone hotspot or Bluetooth on Android.
 */
class NearbyLobbyScreen(
    private val game: PickleRallyGame
) : KtxScreen {
    private val camera = OrthographicCamera()
    private val viewport = FitViewport(450f, 900f, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val titleFont = BitmapFont().apply { data.setScale(2.2f) }
    private val bodyFont = BitmapFont().apply { data.setScale(1.3f) }
    private val smallFont = BitmapFont().apply { data.setScale(1.05f) }
    private val touchPt = Vector2()

    private val hostRect = Rectangle(55f, 650f, 340f, 64f)
    private val retryRect = Rectangle(305f, 574f, 90f, 36f)
    private val backRect = Rectangle(55f, 50f, 340f, 56f)
    private val listTopY = 560f
    private val rowHeight = 62f
    private val rowGap = 10f
    private val maxRows = 6

    private val discovery: NearbyDiscovery?
        get() = game.nearbyService as? NearbyDiscovery

    private fun rowRect(index: Int): Rectangle =
        Rectangle(55f, listTopY - (index + 1) * rowHeight - index * rowGap, 340f, rowHeight)

    private fun currentHosts(): List<NearbyHost> =
        discovery?.nearbyHosts?.value?.take(maxRows) ?: emptyList()

    override fun show() {
        camera.position.set(225f, 450f, 0f)
        camera.update()
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        discovery?.startDiscovery()

        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPt.set(screenX.toFloat(), screenY.toFloat())
                viewport.unproject(touchPt)

                if (backRect.contains(touchPt.x, touchPt.y)) {
                    game.soundService.playMenuClick()
                    discovery?.stopDiscovery()
                    game.openMainMenu()
                    return true
                }
                if (discovery == null) return true

                if (hostRect.contains(touchPt.x, touchPt.y)) {
                    game.soundService.playEnterCourt()
                    discovery?.stopDiscovery()
                    game.useNearby = true
                    game.openCourt(onlineHost = true, doubles = false)
                    return true
                }
                if (retryRect.contains(touchPt.x, touchPt.y)) {
                    game.soundService.playMenuClick()
                    discovery?.startDiscovery()
                    return true
                }
                currentHosts().forEachIndexed { index, host ->
                    if (rowRect(index).contains(touchPt.x, touchPt.y)) {
                        game.soundService.playEnterCourt()
                        game.useNearby = true
                        game.openCourt(onlineHost = false, doubles = false, joinRoomCode = host.roomCode)
                        return true
                    }
                }
                return true
            }

            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    game.soundService.playMenuClick()
                    discovery?.stopDiscovery()
                    game.openMainMenu()
                    return true
                }
                return false
            }
        }
    }

    override fun hide() {
        Gdx.input.setCatchKey(Input.Keys.BACK, false)
        // Keep the found-hosts list (a join may be in progress); just stop scanning.
        discovery?.stopDiscovery()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(CourtColors.SURROUND_NAVY.r, CourtColors.SURROUND_NAVY.g, CourtColors.SURROUND_NAVY.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        viewport.apply()
        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        val available = discovery != null
        val hosts = currentHosts()
        val errorText = discovery?.nearbyError?.value

        shapeRenderer.use(ShapeRenderer.ShapeType.Filled) { sr ->
            sr.color = if (available) Color.valueOf("84CC16") else Color.valueOf("334155")
            sr.rect(hostRect.x, hostRect.y, hostRect.width, hostRect.height)
            sr.color = Color.valueOf("1E293B")
            if (available) {
                sr.rect(retryRect.x, retryRect.y, retryRect.width, retryRect.height)
            }
            hosts.indices.forEach { i ->
                val r = rowRect(i)
                sr.rect(r.x, r.y, r.width, r.height)
            }
            sr.rect(backRect.x, backRect.y, backRect.width, backRect.height)
        }

        batch.use { sb ->
            titleFont.color = Color.valueOf("84CC16")
            titleFont.draw(sb, "NEARBY 1v1", 92f, 840f)
            smallFont.color = Color.valueOf("CBD5E1")
            smallFont.draw(sb, "Same Wi-Fi, hotspot or Bluetooth.", 70f, 775f)
            smallFont.draw(sb, "No account or internet needed.", 70f, 750f)

            bodyFont.color = Color.valueOf("0B1220")
            bodyFont.draw(sb, "Host Nearby Game", 128f, 690f)

            bodyFont.color = Color.WHITE
            bodyFont.draw(sb, "Players nearby", 55f, 600f)
            if (available) {
                smallFont.color = Color.valueOf("84CC16")
                smallFont.draw(sb, "Retry", retryRect.x + 24f, retryRect.y + 24f)
            }
            if (!available) {
                smallFont.color = Color.valueOf("94A3B8")
                smallFont.draw(sb, "Nearby play needs the Android app.", 55f, 540f)
            } else if (!errorText.isNullOrBlank()) {
                smallFont.color = Color.valueOf("FDBA74")
                smallFont.draw(sb, errorText, 55f, 540f)
                smallFont.color = Color.valueOf("94A3B8")
                smallFont.draw(sb, "Enable Bluetooth/Wi-Fi & tap Retry.", 55f, 512f)
            } else if (hosts.isEmpty()) {
                smallFont.color = Color.valueOf("94A3B8")
                val dots = ".".repeat(1 + ((System.currentTimeMillis() / 500) % 3).toInt())
                smallFont.draw(sb, "Searching$dots", 55f, 540f)
                smallFont.draw(sb, "Ask your friend to tap Host Nearby Game.", 55f, 512f)
            } else {
                hosts.forEachIndexed { i, host ->
                    val r = rowRect(i)
                    bodyFont.color = Color.WHITE
                    bodyFont.draw(sb, host.hostName + "'s court", r.x + 16f, r.y + 42f)
                    smallFont.color = Color.valueOf("94A3B8")
                    smallFont.draw(sb, host.roomCode, r.x + 16f, r.y + 20f)
                    bodyFont.color = Color.valueOf("84CC16")
                    bodyFont.draw(sb, "Join", r.x + r.width - 64f, r.y + 38f)
                }
            }

            bodyFont.color = Color.WHITE
            bodyFont.draw(sb, "Back", 203f, 86f)
        }
    }

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        titleFont.dispose()
        bodyFont.dispose()
        smallFont.dispose()
    }
}
