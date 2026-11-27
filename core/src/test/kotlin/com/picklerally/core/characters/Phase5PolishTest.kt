package com.picklerally.core.characters

import com.picklerally.core.rules.FaultReason
import com.picklerally.core.sim.SinglePlayerMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Phase5PolishTest {

    @Test
    fun `character roster defines all 10 cosmetic fighters with expected traits`() {
        assertEquals(10, CharacterId.entries.size)
        assertFalse(CharacterId.RIO.isUnlockable)
        assertTrue(CharacterId.DILL.isUnlockable)

        val lola = CharacterCatalog.get(CharacterId.LOLA)
        assertTrue(lola.hasGlasses)

        val bert = CharacterCatalog.get(CharacterId.BERT)
        assertTrue(bert.hasBeard)
        assertTrue(bert.bodyRadiusFt > CharacterCatalog.get(CharacterId.RIO).bodyRadiusFt)

        val dill = CharacterCatalog.get(CharacterId.DILL)
        assertTrue(dill.isPickleMascot)

        val maya = CharacterCatalog.get(CharacterId.MAYA)
        assertEquals(CharacterId.MAYA, maya.id)

        val duke = CharacterCatalog.get(CharacterId.DUKE)
        assertTrue(duke.bigBuild)
        assertTrue(duke.tankTop)
    }

    @Test
    fun `selected characters apply cleanly to match without stat differences`() {
        val match = SinglePlayerMatch(playerChar = CharacterId.BERT, botChar = CharacterId.DILL)
        assertEquals(CharacterId.BERT, match.player.characterId)
        assertEquals(CharacterId.DILL, match.bot.characterId)
        assertEquals(18.5f, SinglePlayerMatch.PLAYER_SPEED_FT_S)
    }

    @Test
    fun `sprite sheet loader follows 48x48 top-down and 64x64 menu atlas convention`() {
        assertEquals(48, CharacterAssetLoader.INGAME_FRAME_PX)
        assertEquals(64, CharacterAssetLoader.MENU_FRAME_PX)
        assertEquals("characters/rio.atlas", CharacterAssetLoader.atlasPath(CharacterId.RIO))
        assertEquals(
            "forehand_right",
            CharacterAssetLoader.topDownRegionName(TopDownAnimState.FOREHAND, SpriteDirection.RIGHT)
        )
        assertEquals(
            "menu_celebrate",
            CharacterAssetLoader.menuRegionName(MenuAnimState.CELEBRATE)
        )
        assertEquals(4, TopDownAnimState.RUN.expectedFrames)
        assertEquals(2, MenuAnimState.IDLE.expectedFrames)
    }

    @Test
    fun `fault callouts produce human-readable announcements`() {
        assertEquals("Kitchen fault!", FaultReason.KITCHEN_VOLLEY.calloutText)
        assertEquals("Out!", FaultReason.OUT_OF_BOUNDS.calloutText)
        assertEquals("Net!", FaultReason.INTO_NET.calloutText)
        assertEquals("Two-bounce fault!", FaultReason.TWO_BOUNCE_VIOLATION.calloutText)
    }
}
