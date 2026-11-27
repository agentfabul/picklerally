package com.picklerally.app.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.CourtSide
import com.picklerally.app.game.MatchRecordMode
import com.picklerally.app.game.ScoringMode
import kotlinx.serialization.Serializable

@Serializable
enum class HistorySource {
    CLOUD,
    LOCAL;

    companion object {
        fun fromStringOrDefault(raw: String?): HistorySource =
            entries.firstOrNull { it.name == raw } ?: LOCAL
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    val uid: String = "",
    val displayName: String = "Player",
    val characterId: String = CharacterId.RIO.name,
    val wins: Long = 0L,
    val losses: Long = 0L,
    val matchesPlayed: Long = 0L,
    val pointsScored: Long = 0L,
    val bestRally: Long = 0L,
    val rating: Long = 1000L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class MatchRecordDoc(
    @DocumentId val id: String = "",
    val matchId: String = "",
    val roomCode: String? = null,
    val mode: String = MatchRecordMode.SOLO_1V1.name,
    val scoringMode: String = ScoringMode.RALLY.name,
    val createdByUid: String = "",
    val participants: List<String> = emptyList(),
    val playerCharacter: String = CharacterId.RIO.name,
    val partnerCharacter: String? = null,
    val opponentCharacter: String = CharacterId.LOLA.name,
    val winnerSide: String = CourtSide.LOWER.name,
    val scoreLower: Long = 0L,
    val scoreUpper: Long = 0L,
    val bestRally: Long = 0L,
    val validated: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class LeaderboardDoc(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "Player",
    val characterId: String = CharacterId.RIO.name,
    val wins: Long = 0L,
    val losses: Long = 0L,
    val matchesPlayed: Long = 0L,
    val pointsScored: Long = 0L,
    val bestRally: Long = 0L,
    val rating: Long = 1000L,
    @get:PropertyName("isPublic")
    @PropertyName("isPublic")
    val isPublic: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Serializable
data class UserProfileStats(
    val uid: String = "local-player",
    val displayName: String = "Local Player",
    val characterId: CharacterId = CharacterId.RIO,
    val wins: Int = 0,
    val losses: Int = 0,
    val matchesPlayed: Int = 0,
    val pointsScored: Int = 0,
    val bestRally: Int = 0,
    val rating: Int = 1000,
)

@Serializable
data class MatchHistoryItem(
    val matchId: String,
    val roomCode: String? = null,
    val mode: MatchRecordMode = MatchRecordMode.SOLO_1V1,
    val scoringMode: ScoringMode = ScoringMode.RALLY,
    val playerCharacter: CharacterId = CharacterId.RIO,
    val partnerCharacter: CharacterId? = null,
    val opponentCharacter: CharacterId = CharacterId.LOLA,
    val winnerSide: CourtSide = CourtSide.LOWER,
    val scoreLower: Int = 0,
    val scoreUpper: Int = 0,
    val bestRally: Int = 0,
    val validated: Boolean = false,
    val createdAtMs: Long = 0L,
    val source: HistorySource = HistorySource.LOCAL,
)

data class LeaderboardRow(
    val uid: String,
    val displayName: String,
    val characterId: CharacterId,
    val wins: Int,
    val losses: Int,
    val matchesPlayed: Int,
    val bestRally: Int,
    val rating: Int,
)

data class FinishedMatchInput(
    val roomCode: String? = null,
    val mode: MatchRecordMode,
    val scoringMode: ScoringMode,
    val playerCharacter: CharacterId,
    val partnerCharacter: CharacterId? = null,
    val opponentCharacter: CharacterId,
    val winnerSide: CourtSide,
    val scoreLower: Int,
    val scoreUpper: Int,
    val bestRally: Int,
)

data class RecordMatchOutcome(
    val savedToCloud: Boolean,
    val matchId: String,
    val errorMessage: String? = null,
)
