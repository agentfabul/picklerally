package com.picklerally.core.sim

import com.picklerally.core.court.CourtSide
import com.picklerally.core.court.CourtSpec
import com.picklerally.core.net.MatchResultValidator
import com.picklerally.core.rules.FaultReason
import com.picklerally.core.rules.ScoringMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DoublesMatchTest {

    @Test
    fun `2v2 starts at 0-0-2 with four distinct court positions`() {
        val match = DoublesMatch(scoringMode = ScoringMode.SIDE_OUT)
        assertEquals("0 - 0 - 2", match.rules.formattedScoreCall())
        assertEquals(CourtSide.LOWER, match.activeServerSide())
        assertEquals(0, match.activeServerIndex())

        // Lower team occupies right and left halves; Upper team occupies right and left halves
        assertTrue(match.lowerTeam[0].x > CourtSpec.COURT_CENTER_X)
        assertTrue(match.lowerTeam[1].x < CourtSpec.COURT_CENTER_X)
        assertTrue(match.upperTeam[0].x < CourtSpec.COURT_CENTER_X)
        assertTrue(match.upperTeam[1].x > CourtSpec.COURT_CENTER_X)
    }

    @Test
    fun `serving team point swaps right-side player and updates server index`() {
        val match = DoublesMatch(scoringMode = ScoringMode.SIDE_OUT)
        assertEquals(0, match.rules.lowerRightPlayerIndex)

        match.rules.applyRallyOutcome(CourtSide.LOWER, FaultReason.OUT_OF_BOUNDS)
        match.setupPointForServe()

        assertEquals(1, match.rules.scoreLower)
        assertEquals(1, match.rules.lowerRightPlayerIndex)
        assertEquals(0, match.activeServerIndex()) // Odd score puts original index 0 on LEFT to serve
        assertTrue(match.lowerTeam[0].x < CourtSpec.COURT_CENTER_X)
        assertTrue(match.lowerTeam[1].x > CourtSpec.COURT_CENTER_X)
    }

    @Test
    fun `MatchResultValidator enforces win by 2 and minimum 11 points`() {
        val invalid = MatchResultValidator.validateFinishedMatch(
            scoreLower = 11,
            scoreUpper = 10,
            winnerSide = CourtSide.LOWER,
            participants = listOf("uid-a", "uid-b"),
            isOnline = true
        )
        assertFalse(invalid.isValid)

        val valid = MatchResultValidator.validateFinishedMatch(
            scoreLower = 12,
            scoreUpper = 10,
            winnerSide = CourtSide.LOWER,
            participants = listOf("uid-a", "uid-b"),
            isOnline = true
        )
        assertTrue(valid.isValid)
        assertEquals(24, valid.winnerRatingDelta)
    }
}
