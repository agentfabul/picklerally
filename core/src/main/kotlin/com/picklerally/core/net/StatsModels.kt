package com.picklerally.core.net

import com.picklerally.core.characters.CharacterId
import com.picklerally.core.court.CourtSide
import com.picklerally.core.rules.MatchMode
import com.picklerally.core.rules.ScoringMode
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure Kotlin player profile, match history, and leaderboard models in core/.
 * Mirrors the Firestore schema & Cloud Function match validation logic.
 */
data class PlayerProfileStats(
    val uid: String,
    val displayName: String,
    val characterId: CharacterId = CharacterId.RIO,
    val wins: Int = 0,
    val losses: Int = 0,
    val matchesPlayed: Int = 0,
    val pointsScored: Int = 0,
    val bestRally: Int = 0,
    val rating: Int = 1000
) {
    val winRatePercent: Int
        get() = if (matchesPlayed <= 0) 0 else ((wins * 100f) / matchesPlayed).toInt()
}

data class MatchHistoryRecord(
    val matchId: String,
    val roomCode: String? = null,
    val matchMode: MatchMode,
    val scoringMode: ScoringMode,
    val isOnline: Boolean,
    val winnerSide: CourtSide,
    val scoreLower: Int,
    val scoreUpper: Int,
    val bestRally: Int,
    val playerCharacter: CharacterId,
    val partnerCharacter: CharacterId? = null,
    val opponentCharacter: CharacterId,
    val participants: List<String>,
    val validated: Boolean = false,
    val createdAtEpochMs: Long = 0L
)

data class LeaderboardEntry(
    val uid: String,
    val displayName: String,
    val characterId: CharacterId,
    val wins: Int,
    val losses: Int,
    val matchesPlayed: Int,
    val pointsScored: Int,
    val bestRally: Int,
    val rating: Int
)

data class MatchValidationOutcome(
    val isValid: Boolean,
    val reason: String,
    val winnerRatingDelta: Int,
    val loserRatingDelta: Int
)

object MatchResultValidator {
    const val MIN_WINNING_SCORE = 11
    const val MIN_WIN_MARGIN = 2
    const val BASE_RATING = 1000
    const val MIN_RATING = 800

    fun validateFinishedMatch(
        scoreLower: Int,
        scoreUpper: Int,
        winnerSide: CourtSide,
        participants: List<String>,
        isOnline: Boolean
    ): MatchValidationOutcome {
        if (scoreLower < 0 || scoreUpper < 0) {
            return MatchValidationOutcome(false, "Negative scores are invalid", 0, 0)
        }
        val high = max(scoreLower, scoreUpper)
        val margin = abs(scoreLower - scoreUpper)
        if (high < MIN_WINNING_SCORE || margin < MIN_WIN_MARGIN) {
            return MatchValidationOutcome(
                false,
                "Match must reach $MIN_WINNING_SCORE and win by $MIN_WIN_MARGIN",
                0,
                0
            )
        }
        val expectedWinner = if (scoreLower > scoreUpper) CourtSide.LOWER else CourtSide.UPPER
        if (winnerSide != expectedWinner) {
            return MatchValidationOutcome(false, "winnerSide does not match final score", 0, 0)
        }
        val cleanParticipants = participants.filter { it.isNotBlank() }.distinct()
        if (cleanParticipants.isEmpty() || cleanParticipants.size > 4) {
            return MatchValidationOutcome(false, "Participants must contain 1..4 unique UIDs", 0, 0)
        }

        val winDelta = if (isOnline) 24 else 10
        val lossDelta = if (isOnline) -12 else -4
        return MatchValidationOutcome(true, "Valid", winDelta, lossDelta)
    }

    fun nextRating(currentRating: Int, delta: Int): Int =
        max(MIN_RATING, currentRating + delta)
}
