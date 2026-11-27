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
import com.picklerally.core.court.CourtColors
import com.picklerally.core.court.CourtSpec
import com.picklerally.core.rules.ScoringMode
import ktx.app.KtxScreen
import ktx.graphics.use

/**
 * Phase 5 portrait Main Menu screen with touch buttons for Solo Match,
 * Online 1v1 Room, Character Select, Scoring Mode toggle, and Audio Mute.
 */
class MainMenuScreen(
    private val game: PickleRallyGame
) : KtxScreen {
    private val camera = OrthographicCamera()
    private val viewport = FitViewport(450f, 900f, camera)
    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val titleFont = BitmapFont().apply { data.setScale(2.8f) }
    private val bodyFont = BitmapFont().apply { data.setScale(1.3f) }
    private val touchPt = Vector2()

    private val playSoloRect = Rectangle(55f, 366f, 165f, 60f)
    private val playDoublesRect = Rectangle(230f, 366f, 165f, 60f)
    private val onlineRect = Rectangle(55f, 286f, 165f, 58f)
    private val nearbyRect = Rectangle(230f, 286f, 165f, 58f)

    /** Online 1v1 is paused for now; tapping it shows a short "coming soon" note. */
    private val onlineEnabled = false
    private var comingSoonUntilMs = 0L
    private val charsRect = Rectangle(55f, 212f, 340f, 58f)
    private val scoringRect = Rectangle(55f, 138f, 220f, 50f)
    private val muteRect = Rectangle(287f, 138f, 108f, 50f)

    override fun show() {
        camera.position.set(225f, 450f, 0f)
        camera.update()

        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPt.set(screenX.toFloat(), screenY.toFloat())
                viewport.unproject(touchPt)

                when {
                    playSoloRect.contains(touchPt.x, touchPt.y) -> {
                        game.soundService.playEnterCourt()
                        game.openCourt(onlineHost = false, doubles = false)
                    }
                    playDoublesRect.contains(touchPt.x, touchPt.y) -> {
                        game.soundService.playEnterCourt()
                        game.openCourt(onlineHost = false, doubles = true)
                    }
                    onlineRect.contains(touchPt.x, touchPt.y) -> {
                        if (onlineEnabled) {
                            game.soundService.playEnterCourt()
                            game.openCourt(onlineHost = true, doubles = false)
                        } else {
                            game.soundService.playMenuClick()
                            comingSoonUntilMs = System.currentTimeMillis() + 2200L
                        }
                    }
                    nearbyRect.contains(touchPt.x, touchPt.y) -> {
                        game.soundService.playMenuClick()
                        game.openNearbyLobby()
                    }
                    charsRect.contains(touchPt.x, touchPt.y) -> {
                        game.soundService.playMenuClick()
                        game.openCharacterSelect()
                    }
                    scoringRect.contains(touchPt.x, touchPt.y) -> {
                        game.soundService.playMenuClick()
                        game.scoringMode = if (game.scoringMode == ScoringMode.RALLY) {
                            ScoringMode.SIDE_OUT
                        } else {
                            ScoringMode.RALLY
                        }
                    }
                    muteRect.contains(touchPt.x, touchPt.y) -> {
                        game.soundService.isMuted = !game.soundService.isMuted
                        game.soundService.playMenuClick()
                    }
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

        val pal = CharacterCatalog.get(game.selectedCharacter)

        shapeRenderer.use(ShapeRenderer.ShapeType.Filled) { sr ->
            // Decorative court card backdrop
            sr.color = Color.valueOf("121C30")
            sr.rect(35f, 460f, 380f, 240f)
            sr.color = CourtColors.COURT_BLUE
            sr.rect(55f, 480f, 340f, 200f)
            sr.color = CourtColors.KITCHEN_BLUE
            sr.rect(55f, 550f, 340f, 60f)

            // Selected character front preview
            sr.color = pal.shirt
            sr.circle(225f, 570f, 44f * (pal.bodyRadiusFt / 1.12f), 24)
            sr.color = pal.skin
            sr.circle(225f, 610f, 30f, 24)
            sr.color = pal.headwear
            sr.rect(194f, 622f, 62f, 13f)
            sr.color = pal.paddle
            sr.circle(285f, 588f, 20f, 18)

            // Primary CTA + secondary menu buttons
            sr.color = Color.valueOf("84CC16")
            sr.rect(playSoloRect.x, playSoloRect.y, playSoloRect.width, playSoloRect.height)

            sr.color = Color.valueOf("F97316")
            sr.rect(playDoublesRect.x, playDoublesRect.y, playDoublesRect.width, playDoublesRect.height)

            sr.color = Color.valueOf("1E293B")
            sr.rect(onlineRect.x, onlineRect.y, onlineRect.width, onlineRect.height)
            sr.color = Color.valueOf("0F766E")
            sr.rect(nearbyRect.x, nearbyRect.y, nearbyRect.width, nearbyRect.height)
            sr.color = Color.valueOf("1E293B")
            sr.rect(charsRect.x, charsRect.y, charsRect.width, charsRect.height)
            sr.rect(scoringRect.x, scoringRect.y, scoringRect.width, scoringRect.height)
            sr.rect(muteRect.x, muteRect.y, muteRect.width, muteRect.height)
        }

        batch.use { sb ->
            titleFont.color = Color.valueOf("84CC16")
            titleFont.draw(sb, "PICKLE", 142f, 835f)
            titleFont.color = Color.valueOf("F97316")
            titleFont.draw(sb, "RALLY", 154f, 780f)

            bodyFont.color = Color.WHITE
            bodyFont.draw(sb, "Selected: ${game.selectedCharacter.displayName} (${game.selectedCharacter.tagline})", 72f, 505f)

            bodyFont.color = Color.valueOf("0B1220")
            bodyFont.draw(sb, "1v1 Solo", 96f, 404f)
            bodyFont.draw(sb, "2v2 Doubles", 254f, 404f)

            bodyFont.color = Color.WHITE
            bodyFont.draw(sb, "Online 1v1", 92f, 322f)
            bodyFont.draw(sb, "Nearby 1v1", 262f, 322f)
            bodyFont.draw(sb, "Character Select", 145f, 248f)
            val modeLabel = if (game.scoringMode == ScoringMode.RALLY) "Scoring: Rally 11" else "Scoring: Side-Out"
            bodyFont.draw(sb, modeLabel, 72f, 170f)
            bodyFont.draw(sb, if (game.soundService.isMuted) "Muted" else "Sound On", 298f, 170f)

            if (System.currentTimeMillis() < comingSoonUntilMs) {
                bodyFont.color = Color.valueOf("FDBA74")
                bodyFont.draw(sb, "This feature is available soon", 78f, 272f)
            }
        }
    }

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        titleFont.dispose()
        bodyFont.dispose()
    }
}
