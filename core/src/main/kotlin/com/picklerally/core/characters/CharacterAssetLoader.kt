package com.picklerally.core.characters

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable

enum class SpriteDirection(val key: String) {
    UP("up"),
    DOWN("down"),
    LEFT("left"),
    RIGHT("right")
}

enum class TopDownAnimState(val key: String, val expectedFrames: Int, val frameDurationSec: Float) {
    IDLE("idle", 2, 0.35f),
    RUN("run", 4, 0.11f),
    FOREHAND("forehand", 4, 0.06f),
    BACKHAND("backhand", 4, 0.06f),
    SERVE("serve", 4, 0.07f)
}

enum class MenuAnimState(val key: String, val expectedFrames: Int, val frameDurationSec: Float) {
    IDLE("menu_idle", 2, 0.40f),
    CELEBRATE("menu_celebrate", 4, 0.14f)
}

/**
 * Specification-compliant TextureAtlas loader for pixel-art character sprite sheets:
 * - In-game top-down view: 48x48 px frames across 4 directions (idle 2f, run 4f, forehand 4f, backhand 4f, serve 4f)
 * - Front view for menus: 64x64 px frames (menu_idle 2f, menu_celebrate 4f)
 *
 * Gracefully returns null if an atlas is not yet packed in assets/characters/<id>.atlas so
 * procedural shape rendering continues working seamlessly until art is dropped in.
 */
class CharacterAssetLoader : Disposable {
    companion object {
        const val INGAME_FRAME_PX = 48
        const val MENU_FRAME_PX = 64

        fun atlasPath(characterId: CharacterId): String =
            "characters/${characterId.name.lowercase()}.atlas"

        fun topDownRegionName(state: TopDownAnimState, dir: SpriteDirection): String =
            "${state.key}_${dir.key}"

        fun menuRegionName(state: MenuAnimState): String = state.key
    }

    private val atlases = mutableMapOf<CharacterId, TextureAtlas>()

    fun tryLoadAtlas(characterId: CharacterId): TextureAtlas? {
        atlases[characterId]?.let { return it }
        val path = atlasPath(characterId)
        val file = Gdx.files?.internal(path) ?: return null
        if (!file.exists()) return null

        return runCatching {
            TextureAtlas(file).also { atlases[characterId] = it }
        }.getOrNull()
    }

    fun getTopDownAnimation(
        characterId: CharacterId,
        state: TopDownAnimState,
        dir: SpriteDirection
    ): Animation<TextureRegion>? {
        val atlas = tryLoadAtlas(characterId) ?: return null
        val regions = atlas.findRegions(topDownRegionName(state, dir))
        if (regions.size == 0) return null
        val playMode = if (state == TopDownAnimState.IDLE || state == TopDownAnimState.RUN) {
            Animation.PlayMode.LOOP
        } else {
            Animation.PlayMode.NORMAL
        }
        return Animation(state.frameDurationSec, regions, playMode)
    }

    fun getMenuAnimation(
        characterId: CharacterId,
        state: MenuAnimState
    ): Animation<TextureRegion>? {
        val atlas = tryLoadAtlas(characterId) ?: return null
        val regions = atlas.findRegions(menuRegionName(state))
        if (regions.size == 0) return null
        return Animation(state.frameDurationSec, regions, Animation.PlayMode.LOOP)
    }

    override fun dispose() {
        atlases.values.forEach { it.dispose() }
        atlases.clear()
    }
}
