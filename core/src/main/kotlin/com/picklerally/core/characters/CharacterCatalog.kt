package com.picklerally.core.characters

import com.badlogic.gdx.graphics.Color

enum class CharacterId(
    val displayName: String,
    val tagline: String,
    val description: String,
    val isUnlockable: Boolean = false
) {
    RIO("Rio", "Speedy rookie", "Orange headband, blue shirt"),
    LOLA("Lola", "Dink master", "Pink visor, gray hair, glasses"),
    BERT("Bert", "Power driver", "Big build, beard, green tank top"),
    DILL("Dill", "Pickle mascot", "Unlockable pickle mascot, red headband", isUnlockable = true),
    MAYA("Maya", "Spin specialist", "Teal visor, purple shirt, black ponytail"),
    KAI("Kai", "Kitchen king", "Yellow headband, red shirt, glasses"),
    ZOE("Zoe", "Lob queen", "White headband, pink shirt, red hair"),
    GUS("Gus", "Veteran grinder", "Gray beard, navy polo, green headband"),
    NINA("Nina", "Net rusher", "Black headband, orange shirt, glasses"),
    DUKE("Duke", "Hard hitter", "Big build, purple tank top, gold headband")
}

data class CharacterPalette(
    val id: CharacterId,
    val skin: Color,
    val shirt: Color,
    val headwear: Color,
    val paddle: Color,
    val hair: Color,
    val hasGlasses: Boolean = false,
    val hasBeard: Boolean = false,
    val isPickleMascot: Boolean = false,
    val bigBuild: Boolean = false,
    val tankTop: Boolean = false,
    val bodyRadiusFt: Float = 1.12f
)

/**
 * Cosmetic-only roster definitions. All characters share identical gameplay movement speed,
 * hit reach, and shot power so matches remain purely skill-based.
 */
object CharacterCatalog {
    val palettes: Map<CharacterId, CharacterPalette> = mapOf(
        CharacterId.RIO to CharacterPalette(
            id = CharacterId.RIO,
            skin = Color.valueOf("F3C69F"),
            shirt = Color.valueOf("2563EB"),
            headwear = Color.valueOf("F97316"),
            paddle = Color.valueOf("84CC16"),
            hair = Color.valueOf("4B2E1E")
        ),
        CharacterId.LOLA to CharacterPalette(
            id = CharacterId.LOLA,
            skin = Color.valueOf("F0C2A2"),
            shirt = Color.valueOf("7C3AED"),
            headwear = Color.valueOf("EC4899"),
            paddle = Color.valueOf("38BDF8"),
            hair = Color.valueOf("94A3B8"),
            hasGlasses = true
        ),
        CharacterId.BERT to CharacterPalette(
            id = CharacterId.BERT,
            skin = Color.valueOf("E9B68E"),
            shirt = Color.valueOf("16A34A"),
            headwear = Color.valueOf("78350F"),
            paddle = Color.valueOf("F59E0B"),
            hair = Color.valueOf("78350F"),
            hasBeard = true,
            bodyRadiusFt = 1.28f
        ),
        CharacterId.DILL to CharacterPalette(
            id = CharacterId.DILL,
            skin = Color.valueOf("65A30D"),
            shirt = Color.valueOf("4D7C0F"),
            headwear = Color.valueOf("EF4444"),
            paddle = Color.valueOf("FACC15"),
            hair = Color.valueOf("3F6212"),
            isPickleMascot = true
        ),
        CharacterId.MAYA to CharacterPalette(
            id = CharacterId.MAYA,
            skin = Color.valueOf("C68642"),
            shirt = Color.valueOf("9333EA"),
            headwear = Color.valueOf("14B8A6"),
            paddle = Color.valueOf("FACC15"),
            hair = Color.valueOf("111827")
        ),
        CharacterId.KAI to CharacterPalette(
            id = CharacterId.KAI,
            skin = Color.valueOf("8D5524"),
            shirt = Color.valueOf("DC2626"),
            headwear = Color.valueOf("FACC15"),
            paddle = Color.valueOf("22D3EE"),
            hair = Color.valueOf("1F2937"),
            hasGlasses = true
        ),
        CharacterId.ZOE to CharacterPalette(
            id = CharacterId.ZOE,
            skin = Color.valueOf("FFDBAC"),
            shirt = Color.valueOf("EC4899"),
            headwear = Color.valueOf("F8FAFC"),
            paddle = Color.valueOf("A3E635"),
            hair = Color.valueOf("B91C1C")
        ),
        CharacterId.GUS to CharacterPalette(
            id = CharacterId.GUS,
            skin = Color.valueOf("E0AC69"),
            shirt = Color.valueOf("1E3A8A"),
            headwear = Color.valueOf("15803D"),
            paddle = Color.valueOf("F97316"),
            hair = Color.valueOf("9CA3AF"),
            hasBeard = true
        ),
        CharacterId.NINA to CharacterPalette(
            id = CharacterId.NINA,
            skin = Color.valueOf("F1C27D"),
            shirt = Color.valueOf("EA580C"),
            headwear = Color.valueOf("0F172A"),
            paddle = Color.valueOf("EC4899"),
            hair = Color.valueOf("3B2314"),
            hasGlasses = true
        ),
        CharacterId.DUKE to CharacterPalette(
            id = CharacterId.DUKE,
            skin = Color.valueOf("5C3A21"),
            shirt = Color.valueOf("7C3AED"),
            headwear = Color.valueOf("EAB308"),
            paddle = Color.valueOf("F43F5E"),
            hair = Color.valueOf("0B0B0B"),
            bigBuild = true,
            tankTop = true,
            bodyRadiusFt = 1.28f
        )
    )

    fun get(id: CharacterId): CharacterPalette = palettes.getValue(id)
}
