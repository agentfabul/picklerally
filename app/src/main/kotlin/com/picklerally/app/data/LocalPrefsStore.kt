package com.picklerally.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.CourtSide
import com.picklerally.app.game.MatchValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.pickleRallyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pickle_rally_prefs",
)

enum class DominantHand(val label: String) {
    RIGHT("Right"),
    LEFT("Left");

    companion object {
        fun fromStringOrDefault(raw: String?, fallback: DominantHand = RIGHT): DominantHand =
            entries.firstOrNull { it.name == raw } ?: fallback
    }
}

data class AppPreferencesSnapshot(
    val selectedCharacter: CharacterId = CharacterId.RIO,
    val partnerCharacter: CharacterId = CharacterId.DILL,
    val opponentCharacter: CharacterId = CharacterId.LOLA,
    val dillUnlocked: Boolean = false,
    val muted: Boolean = false,
    val hasSeenControlsHint: Boolean = false,
    val dominantHand: DominantHand = DominantHand.RIGHT,
    val localProfile: UserProfileStats = UserProfileStats(),
    val localHistory: List<MatchHistoryItem> = emptyList(),
)

class LocalPrefsStore(context: Context) {
    private val dataStore = context.applicationContext.pickleRallyDataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val SELECTED_CHAR = stringPreferencesKey("selected_character")
        val PARTNER_CHAR = stringPreferencesKey("partner_character")
        val OPPONENT_CHAR = stringPreferencesKey("opponent_character")
        val DILL_UNLOCKED = booleanPreferencesKey("dill_unlocked")
        val MUTED = booleanPreferencesKey("muted")
        val CONTROLS_HINT_SEEN = booleanPreferencesKey("controls_hint_seen")
        val DOMINANT_HAND = stringPreferencesKey("dominant_hand")
        val LOCAL_PROFILE = stringPreferencesKey("local_profile_json")
        val LOCAL_HISTORY = stringPreferencesKey("local_history_json")
    }

    val preferencesFlow: Flow<AppPreferencesSnapshot> = dataStore.data.map { prefs ->
        val selected = CharacterId.fromStringOrDefault(prefs[Keys.SELECTED_CHAR], CharacterId.RIO)
        val partner = CharacterId.fromStringOrDefault(prefs[Keys.PARTNER_CHAR], CharacterId.DILL)
        val opponent = CharacterId.fromStringOrDefault(prefs[Keys.OPPONENT_CHAR], CharacterId.LOLA)
        val dillUnlocked = prefs[Keys.DILL_UNLOCKED] ?: (selected == CharacterId.DILL)
        val muted = prefs[Keys.MUTED] ?: false
        val hintSeen = prefs[Keys.CONTROLS_HINT_SEEN] ?: false
        val dominantHand = DominantHand.fromStringOrDefault(prefs[Keys.DOMINANT_HAND], DominantHand.RIGHT)
        val profile = decodeProfile(prefs[Keys.LOCAL_PROFILE], selected)
        val history = decodeHistory(prefs[Keys.LOCAL_HISTORY])

        AppPreferencesSnapshot(
            selectedCharacter = selected,
            partnerCharacter = partner,
            opponentCharacter = opponent,
            dillUnlocked = dillUnlocked,
            muted = muted,
            hasSeenControlsHint = hintSeen,
            dominantHand = dominantHand,
            localProfile = profile,
            localHistory = history,
        )
    }

    suspend fun setSelectedCharacter(characterId: CharacterId) {
        dataStore.edit { prefs ->
            prefs[Keys.SELECTED_CHAR] = characterId.name
            if (characterId == CharacterId.DILL) {
                prefs[Keys.DILL_UNLOCKED] = true
            }
            val profile = decodeProfile(prefs[Keys.LOCAL_PROFILE], characterId)
            prefs[Keys.LOCAL_PROFILE] = json.encodeToString(
                UserProfileStats.serializer(),
                profile.copy(characterId = characterId),
            )
        }
    }

    suspend fun setPartnerCharacter(characterId: CharacterId) {
        dataStore.edit { prefs ->
            prefs[Keys.PARTNER_CHAR] = characterId.name
        }
    }

    suspend fun setOpponentCharacter(characterId: CharacterId) {
        dataStore.edit { prefs ->
            prefs[Keys.OPPONENT_CHAR] = characterId.name
        }
    }

    suspend fun setDillUnlocked(unlocked: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.DILL_UNLOCKED] = unlocked
            if (unlocked) {
                prefs[Keys.SELECTED_CHAR] = CharacterId.DILL.name
            } else if (prefs[Keys.SELECTED_CHAR] == CharacterId.DILL.name) {
                prefs[Keys.SELECTED_CHAR] = CharacterId.RIO.name
            }
        }
    }

    suspend fun setMuted(muted: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.MUTED] = muted
        }
    }

    suspend fun markControlsHintSeen() {
        dataStore.edit { prefs ->
            prefs[Keys.CONTROLS_HINT_SEEN] = true
        }
    }

    suspend fun setDominantHand(hand: DominantHand) {
        dataStore.edit { prefs ->
            prefs[Keys.DOMINANT_HAND] = hand.name
        }
    }

    suspend fun getLocalProfile(fallbackCharacter: CharacterId): UserProfileStats {
        val prefs = dataStore.data.first()
        return decodeProfile(prefs[Keys.LOCAL_PROFILE], fallbackCharacter)
    }

    suspend fun updateLocalDisplayName(
        displayName: String,
        fallbackCharacter: CharacterId,
    ): String {
        val clean = displayName.trim().take(32).ifEmpty { "Local Player" }
        dataStore.edit { prefs ->
            val current = decodeProfile(prefs[Keys.LOCAL_PROFILE], fallbackCharacter)
            prefs[Keys.LOCAL_PROFILE] = json.encodeToString(
                UserProfileStats.serializer(),
                current.copy(displayName = clean, characterId = fallbackCharacter),
            )
        }
        return clean
    }

    suspend fun getLocalHistory(): List<MatchHistoryItem> {
        val prefs = dataStore.data.first()
        return decodeHistory(prefs[Keys.LOCAL_HISTORY])
    }

    suspend fun appendLocalMatch(
        matchId: String,
        input: FinishedMatchInput,
        ratingDelta: Int,
    ) {
        val item = MatchHistoryItem(
            matchId = matchId,
            roomCode = input.roomCode,
            mode = input.mode,
            scoringMode = input.scoringMode,
            playerCharacter = input.playerCharacter,
            partnerCharacter = input.partnerCharacter,
            opponentCharacter = input.opponentCharacter,
            winnerSide = input.winnerSide,
            scoreLower = input.scoreLower,
            scoreUpper = input.scoreUpper,
            bestRally = input.bestRally,
            validated = false,
            createdAtMs = System.currentTimeMillis(),
            source = HistorySource.LOCAL,
        )

        dataStore.edit { prefs ->
            val currentHistory = decodeHistory(prefs[Keys.LOCAL_HISTORY])
            val updatedHistory = (listOf(item) + currentHistory).take(25)
            prefs[Keys.LOCAL_HISTORY] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(MatchHistoryItem.serializer()),
                updatedHistory,
            )

            val currentProfile = decodeProfile(prefs[Keys.LOCAL_PROFILE], input.playerCharacter)
            val won = input.winnerSide == CourtSide.LOWER
            val updatedProfile = currentProfile.copy(
                characterId = input.playerCharacter,
                wins = currentProfile.wins + if (won) 1 else 0,
                losses = currentProfile.losses + if (won) 0 else 1,
                matchesPlayed = currentProfile.matchesPlayed + 1,
                pointsScored = currentProfile.pointsScored + input.scoreLower,
                bestRally = maxOf(currentProfile.bestRally, input.bestRally),
                rating = MatchValidator.nextPlayerRating(currentProfile.rating, ratingDelta),
            )
            prefs[Keys.LOCAL_PROFILE] = json.encodeToString(
                UserProfileStats.serializer(),
                updatedProfile,
            )
        }
    }

    private fun decodeProfile(raw: String?, fallbackCharacter: CharacterId): UserProfileStats {
        if (raw.isNullOrBlank()) {
            return UserProfileStats(characterId = fallbackCharacter)
        }
        return try {
            json.decodeFromString(UserProfileStats.serializer(), raw)
        } catch (_: Exception) {
            UserProfileStats(characterId = fallbackCharacter)
        }
    }

    private fun decodeHistory(raw: String?): List<MatchHistoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(MatchHistoryItem.serializer()),
                raw,
            )
        } catch (_: Exception) {
            emptyList()
        }
    }
}
