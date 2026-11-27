package com.picklerally.core.rules

import com.picklerally.core.court.CourtSide
import com.picklerally.core.court.CourtSpec
import com.picklerally.core.court.ServiceBoxSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RulesEngineTest {

    @Test
    fun `serve must originate behind baseline and land diagonally past NVZ line`() {
        val rules = RulesEngine(ScoringMode.RALLY, MatchMode.SINGLES)
        val assignment = rules.currentServeAssignment()

        // Lower at 0 points (even) serves from right box (x >= 14) to upper-left box (x <= 14)
        assertEquals(CourtSide.LOWER, assignment.servingSide)
        assertEquals(ServiceBoxSide.RIGHT, assignment.serverBox)
        assertEquals(CourtSide.UPPER, assignment.targetCourtSide)
        assertEquals(ServiceBoxSide.LEFT, assignment.targetBox)

        // Stepping inside baseline triggers foot fault
        assertEquals(
            FaultReason.SERVE_FOOT_FAULT,
            rules.beginServe(serverX = 18f, serverY = CourtSpec.COURT_BOTTOM_Y + 0.5f)
        )
        // Behind baseline succeeds
        assertNull(rules.beginServe(serverX = 18f, serverY = CourtSpec.COURT_BOTTOM_Y - 0.5f))

        // Landing in target diagonal box past NVZ line is valid
        assertTrue(rules.isServeLandingValid(9f, 40f, assignment))
        // Landing ON the NVZ line is a short serve fault
        assertFalse(rules.isServeLandingValid(9f, CourtSpec.UPPER_NVZ_Y, assignment))
        // Landing in wrong service box is invalid
        assertFalse(rules.isServeLandingValid(19f, 40f, assignment))
    }

    @Test
    fun `two-bounce rule requires return and third shot to bounce before hit`() {
        val rules = RulesEngine(ScoringMode.RALLY, MatchMode.SINGLES)
        assertNull(rules.beginServe(18f, CourtSpec.COURT_BOTTOM_Y - 1f)) // rallyShotCount = 1

        // Shot 2 (return of serve): volleying without bounce is a two-bounce violation
        assertEquals(
            FaultReason.TWO_BOUNCE_VIOLATION,
            rules.validatePlayerHit(CourtSide.UPPER, 9f, 40f, bouncesOnCurrentSide = 0)
        )
        // Return after 1 bounce is legal -> advances to rallyShotCount = 2
        assertNull(rules.validatePlayerHit(CourtSide.UPPER, 9f, 40f, bouncesOnCurrentSide = 1))

        // Shot 3 (third shot): volleying without bounce is ALSO a two-bounce violation
        assertEquals(
            FaultReason.TWO_BOUNCE_VIOLATION,
            rules.validatePlayerHit(CourtSide.LOWER, 14f, 16f, bouncesOnCurrentSide = 0)
        )
        // Third shot after 1 bounce is legal -> advances to rallyShotCount = 3
        assertNull(rules.validatePlayerHit(CourtSide.LOWER, 14f, 16f, bouncesOnCurrentSide = 1))

        // Shot 4+: volleying outside kitchen is now legal
        assertNull(rules.validatePlayerHit(CourtSide.UPPER, 14f, 36.5f, bouncesOnCurrentSide = 0))
    }

    @Test
    fun `kitchen rule forbids volleying while standing in kitchen or on NVZ line`() {
        val rules = RulesEngine(ScoringMode.RALLY, MatchMode.SINGLES)
        rules.beginServe(18f, CourtSpec.COURT_BOTTOM_Y - 1f)
        rules.validatePlayerHit(CourtSide.UPPER, 9f, 40f, bouncesOnCurrentSide = 1)
        rules.validatePlayerHit(CourtSide.LOWER, 14f, 16f, bouncesOnCurrentSide = 1)

        // Now rallyShotCount = 3 (volleys allowed outside NVZ)
        // Volleying inside lower kitchen (y = 24) -> Kitchen fault!
        assertEquals(
            FaultReason.KITCHEN_VOLLEY,
            rules.validatePlayerHit(CourtSide.LOWER, 14f, 24f, bouncesOnCurrentSide = 0)
        )
        // Volleying ON the NVZ line (y = 21) -> Kitchen fault!
        assertEquals(
            FaultReason.KITCHEN_VOLLEY,
            rules.validatePlayerHit(CourtSide.LOWER, 14f, CourtSpec.LOWER_NVZ_Y, bouncesOnCurrentSide = 0)
        )
        // Hitting a bounced ball inside the kitchen is legal!
        assertNull(rules.validatePlayerHit(CourtSide.LOWER, 14f, 24f, bouncesOnCurrentSide = 1))
    }

    @Test
    fun `rally scoring awards point every rally and requires win by 2 at 11`() {
        val rules = RulesEngine(ScoringMode.RALLY, MatchMode.SINGLES, pointsToWin = 11, winByMargin = 2)

        // Bring score to 10-10
        repeat(10) {
            rules.resolveFault(CourtSide.UPPER, FaultReason.OUT_OF_BOUNDS)
            rules.resolveFault(CourtSide.LOWER, FaultReason.OUT_OF_BOUNDS)
        }
        assertEquals(10, rules.scoreLower)
        assertEquals(10, rules.scoreUpper)
        assertFalse(rules.isMatchOver)

        // 11-10 does NOT win (margin = 1)
        val atEleven = rules.resolveFault(CourtSide.UPPER, FaultReason.INTO_NET)
        assertEquals(11, rules.scoreLower)
        assertNull(atEleven.matchWinner)
        assertFalse(rules.isMatchOver)

        // 12-10 wins (margin = 2)
        val winPoint = rules.resolveFault(CourtSide.UPPER, FaultReason.DOUBLE_BOUNCE)
        assertEquals(12, rules.scoreLower)
        assertEquals(CourtSide.LOWER, winPoint.matchWinner)
        assertTrue(rules.isMatchOver)
    }

    @Test
    fun `side-out scoring in singles only scores on own serve and switches server on side-out`() {
        val rules = RulesEngine(ScoringMode.SIDE_OUT, MatchMode.SINGLES)
        assertEquals(CourtSide.LOWER, rules.servingSide)

        // Lower (server) wins rally -> 1-0, keeps serve, moves to LEFT (odd) service box
        val p1 = rules.resolveFault(CourtSide.UPPER, FaultReason.OUT_OF_BOUNDS)
        assertEquals(1, rules.scoreLower)
        assertEquals(CourtSide.LOWER, p1.pointAwardedTo)
        assertFalse(p1.sideOutOccurred)
        assertEquals(ServiceBoxSide.LEFT, rules.currentServeAssignment().serverBox)

        // Lower (server) faults -> side-out, NO point awarded to Upper, Upper becomes server
        val p2 = rules.resolveFault(CourtSide.LOWER, FaultReason.INTO_NET)
        assertEquals(1, rules.scoreLower)
        assertEquals(0, rules.scoreUpper)
        assertNull(p2.pointAwardedTo)
        assertTrue(p2.sideOutOccurred)
        assertEquals(CourtSide.UPPER, rules.servingSide)
    }

    @Test
    fun `doubles side-out starts 0-0-2 and rotates server 1, server 2, and side switches`() {
        val rules = RulesEngine(ScoringMode.SIDE_OUT, MatchMode.DOUBLES)
        assertEquals("0 - 0 - 2", rules.formattedScoreCall())
        assertEquals(0, rules.lowerRightPlayerIndex)

        // Lower scores on opening serve -> teammates swap, score becomes 1-0-2
        rules.resolveFault(CourtSide.UPPER, FaultReason.OUT_OF_BOUNDS)
        assertEquals("1 - 0 - 2", rules.formattedScoreCall())
        assertEquals(1, rules.lowerRightPlayerIndex)

        // Lower faults on opening turn -> immediate side-out to Upper at server #1 (0-1-1)
        val openSideOut = rules.resolveFault(CourtSide.LOWER, FaultReason.INTO_NET)
        assertTrue(openSideOut.sideOutOccurred)
        assertEquals(CourtSide.UPPER, rules.servingSide)
        assertEquals("0 - 1 - 1", rules.formattedScoreCall())

        // Upper server #1 faults -> advances to server #2 (does NOT side-out yet)
        val toServerTwo = rules.resolveFault(CourtSide.UPPER, FaultReason.OUT_OF_BOUNDS)
        assertFalse(toServerTwo.sideOutOccurred)
        assertEquals("0 - 1 - 2", rules.formattedScoreCall())

        // Upper server #2 faults -> side-out back to Lower at server #1
        val fullSideOut = rules.resolveFault(CourtSide.UPPER, FaultReason.KITCHEN_VOLLEY)
        assertTrue(fullSideOut.sideOutOccurred)
        assertEquals(CourtSide.LOWER, rules.servingSide)
        assertEquals(1, rules.serverNumberCall)
    }
}
