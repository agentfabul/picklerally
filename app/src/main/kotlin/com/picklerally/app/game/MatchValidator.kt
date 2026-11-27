package com.picklerally.app.game

import kotlin.math.abs
import kotlin.math.max

enum class MatchRecordMode {
    SOLO_1V1,
    SOLO_2V2,
    SINGLES_1V1,
    DOUBLES_2V2;

    companion object {
        fun fromStringOrDefault(raw: String?, fallback: MatchRecordMode = SOLO_1V1): MatchRecordMode =
            entries.firstOrNull { it.name == raw } ?: fallback
    }
}

data class SubmittedMatchPayload(
    val matchId: String? = null,
    val roomCode: String? = null,
    val mode: MatchRecordMode,
    val scoringMode: ScoringMode,
    val createdByUid: String,
    val participants: List<String>,
    val scoreLower: Int,
    val scoreUpper: Int,
    val winnerSide: CourtSide,
    val bestRally: Int,
    val playerCharacter: CharacterId,
    val partnerCharacter: CharacterId? = null,
    val opponentCharacter: CharacterId,
)

data class MatchValidationOutcome(
    val isValid: Boolean,
    val reason: String,
    val isOnline: Boolean,
    val winnerRatingDelta: Int,
    val loserRatingDelta: Int,
)

object MatchValidator {
    private val uidRegex = Regex("^[a-zA-Z0-9_-]{1,128}$")

    fun validateMatchPayload(payload: SubmittedMatchPayload): MatchValidationOutcome {
        if (!uidRegex.matches(payload.createdByUid)) {
            return invalid("Invalid creator UID")
        }
        if (payload.participants.isEmpty() || payload.participants.size > 4) {
            return invalid("Participants must have 1 to 4 entries")
        }
        val uniqueUids = payload.participants.toSet()
        if (uniqueUids.size != payload.participants.size) {
            return invalid("Duplicate participant UIDs are not allowed")
        }
        for (uid in payload.participants) {
            if (!uidRegex.matches(uid)) {
                return invalid("Invalid participant UID format")
            }
        }
        if (!uniqueUids.contains(payload.createdByUid)) {
            return invalid("Creator UID must be included in participants")
        }
        if (
            payload.scoreLower < 0 ||
            payload.scoreUpper < 0 ||
            payload.scoreLower > 99 ||
            payload.scoreUpper > 99
        ) {
            return invalid("Scores must be integers between 0 and 99")
        }
        val high = max(payload.scoreLower, payload.scoreUpper)
        val margin = abs(payload.scoreLower - payload.scoreUpper)
        if (high < 11 || margin < 2) {
            return invalid("Finished match must reach 11 points and win by 2")
        }
        val expectedWinner = if (payload.scoreLower > payload.scoreUpper) CourtSide.LOWER else CourtSide.UPPER
        if (payload.winnerSide != expectedWinner) {
            return invalid("winnerSide does not match final scores")
        }
        if (payload.bestRally < 0 || payload.bestRally > 999) {
            return invalid("bestRally out of bounds")
        }

        val isOnline = payload.mode == MatchRecordMode.SINGLES_1V1 || payload.mode == MatchRecordMode.DOUBLES_2V2
        return MatchValidationOutcome(
            isValid = true,
            reason = "Validated",
            isOnline = isOnline,
            winnerRatingDelta = if (isOnline) 24 else 10,
            loserRatingDelta = if (isOnline) -12 else -4,
        )
    }

    fun nextPlayerRating(currentRating: Int, delta: Int): Int {
        val safeBase = if (currentRating <= 0) 1000 else currentRating
        return max(800, safeBase + delta).coerceAtMost(5000)
    }

    private fun invalid(reason: String) = MatchValidationOutcome(
        isValid = false,
        reason = reason,
        isOnline = false,
        winnerRatingDelta = 0,
        loserRatingDelta = 0,
    )
}
