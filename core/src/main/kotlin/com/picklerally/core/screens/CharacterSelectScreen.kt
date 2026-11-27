package com.picklerally.core.screens

import com.badlogic.gdx.Gdx
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
import com.picklerally.core.characters.CharacterCatalog
import com.picklerally.core.characters.CharacterId
import com.picklerally.core.court.CourtColors
import ktx.app.KtxScreen
import ktx.graphics.use

/**
 * Phase 5 Character Select screen for Rio, Lola, Bert, and unlockable Dill.
 * Cosmetic-only roster; zero stat differences across characters.
 */
class CharacterSelectScreen(
    private val game: PickleRallyGame
) : KtxScreen {
    private val camera = OrthographicCamera()
    private val viewport = FitViewport(450f, 900f, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val headerFont = BitmapFont().apply { data.setScale(2.0f) }
    private val bodyFont = BitmapFont().apply { data.setScale(1.25f) }
    private val touchPt = Vector2()

    private val cardRects = CharacterId.entries.mapIndexed { idx, id ->
        val col = idx % 2
        val row = idx / 2
        val x = 35f + col * 195f
        val y = 665f - row * 114f
        id to Rectangle(x, y, 185f, 104f)
    }
    private val unlockDillRect = Rectangle(35f, 138f, 380f, 52f)
    private val backRect = Rectangle(35f, 64f, 380f, 58f)

    override fun show() {
        camera.position.set(225f, 450f, 0f)
        camera.update()

        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPt.set(screenX.toFloat(), screenY.toFloat())
                viewport.unproject(touchPt)

                for ((id, rect) in cardRects) {
                    if (rect.contains(touchPt.x, touchPt.y)) {
                        if (!id.isUnlockable || game.dillUnlocked) {
                            game.selectedCharacter = id
                            if (game.opponentCharacter == id) {
                                game.opponentCharacter = if (id == CharacterId.LOLA) CharacterId.RIO else CharacterId.LOLA
                            }
                            game.soundService.playMenuClick()
                        }
                        return true
                    }
                }

                if (unlockDillRect.contains(touchPt.x, touchPt.y)) {
                    game.dillUnlocked = !game.dillUnlocked
                    if (game.dillUnlocked) game.selectedCharacter = CharacterId.DILL
                    else if (game.selectedCharacter == CharacterId.DILL) game.selectedCharacter = CharacterId.RIO
                    game.soundService.playMenuClick()
                } else if (backRect.contains(touchPt.x, touchPt.y)) {
                    game.soundService.playMenuClick()
                    game.openMainMenu()
                }
                return true
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(
            CourtColors.SURROUND_NAVY.r,
            CourtColors.SURROUND_NAVY.g,
            CourtColors.SURROUND_NAVY.b,
            1f
        )
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        viewport.apply()
        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        shapeRenderer.use(ShapeRenderer.ShapeType.Filled) { sr ->
            for ((id, rect) in cardRects) {
                val selected = game.selectedCharacter == id
                val locked = id.isUnlockable && !game.dillUnlocked
                sr.color = if (selected) Color.valueOf("1D56B8") else Color.valueOf("121C30")
                sr.rect(rect.x, rect.y, rect.width, rect.height)

                val pal = CharacterCatalog.get(id)
                val cx = rect.x + 38f
                val cy = rect.y + rect.height / 2f
                sr.color = if (locked) Color(0.25f, 0.28f, 0.35f, 1f) else pal.shirt
                sr.circle(cx, cy - 10f, 18f * (pal.bodyRadiusFt / 1.12f), 20)
                sr.color = if (locked) Color(0.4f, 0.44f, 0.5f, 1f) else pal.skin
                sr.circle(cx, cy + 10f, 13f, 20)
                sr.color = pal.headwear
                sr.rect(cx - 14f, cy + 16f, 28f, 6f)
                sr.color = pal.paddle
                sr.circle(cx + 22f, cy, 8f, 14)
            }

            sr.color = Color.valueOf("1E293B")
            sr.rect(unlockDillRect.x, unlockDillRect.y, unlockDillRect.width, unlockDillRect.height)

            sr.color = Color.valueOf("84CC16")
            sr.rect(backRect.x, backRect.y, backRect.width, backRect.height)
        }

        batch.use { sb ->
            headerFont.color = Color.WHITE
            headerFont.draw(sb, "Select Player", 35f, 845f)
            bodyFont.color = Color.LIGHT_GRAY
            bodyFont.draw(sb, "Cosmetic styles only · Equal court speed", 35f, 800f)

            for ((id, rect) in cardRects) {
                val locked = id.isUnlockable && !game.dillUnlocked
                bodyFont.color = if (game.selectedCharacter == id) Color.valueOf("84CC16") else Color.WHITE
                bodyFont.draw(sb, if (locked) "${id.displayName} (Lock)" else id.displayName, rect.x + 74f, rect.y + 72f)
                bodyFont.color = Color.LIGHT_GRAY
                bodyFont.draw(sb, id.tagline, rect.x + 74f, rect.y + 42f)
            }

            bodyFont.color = Color.WHITE
            bodyFont.draw(sb, if (game.dillUnlocked) "Dill Unlocked · Tap to Lock" else "Unlock Dill Mascot", 82f, 172f)

            bodyFont.color = Color.valueOf("0B1220")
            bodyFont.draw(sb, "Confirm & Back to Menu", 118f, 100f)
        }
    }

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        headerFont.dispose()
        bodyFont.dispose()
    }
}
