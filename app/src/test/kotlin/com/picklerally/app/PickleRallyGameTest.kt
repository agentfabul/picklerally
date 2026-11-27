package com.picklerally.app

import com.picklerally.app.game.BallState
import com.picklerally.app.game.BotDifficulty
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.CourtSide
import com.picklerally.app.game.CourtSpec
import com.picklerally.app.game.CourtZone
import com.picklerally.app.game.DoublesMatch
import com.picklerally.app.game.FaultReason
import com.picklerally.app.game.MatchFormatConfig
import com.picklerally.app.game.MatchRecordMode
import com.picklerally.app.game.MatchStateSnapshot
import com.picklerally.app.game.MatchValidator
import com.picklerally.app.game.NetCrossOutcome
import com.picklerally.app.game.OnlineMatch1v1
import com.picklerally.app.game.PlayerInputSnapshot
import com.picklerally.app.game.RallyPhase
import com.picklerally.app.game.RulesEngine
import com.picklerally.app.game.ScoringMode
import com.picklerally.app.game.ServiceBoxSide
import com.picklerally.app.game.ShotType
import com.picklerally.app.game.SinglePlayerMatch
import com.picklerally.app.game.SubmittedMatchPayload
import com.picklerally.app.game.SwipeVector
import com.picklerally.app.game.classifySwipe
import com.picklerally.app.game.computeTargetShotVelocity
import com.picklerally.app.game.stepBallPhysics
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickleRallyGameTest {

    @Test
    fun courtSpec_dimensionsNetSagAndZonesAreExact() {
        assertTrue(CourtSpec.isInBounds(14f, 28f))
        assertFalse(CourtSpec.isInBounds(3.5f, 28f))
        assertTrue(CourtSpec.isInKitchen(14f, 24f, CourtSide.LOWER))
        assertFalse(CourtSpec.isInKitchen(14f, 24f, CourtSide.UPPER))
        assertTrue(CourtSpec.isInServiceBox(18f, 12f, CourtSide.LOWER, ServiceBoxSide.RIGHT))
        assertEquals(CourtZone.OUT_OF_BOUNDS, CourtSpec.classifyPoint(1f, 10f))
        assertEquals(CourtZone.LOWER_KITCHEN, CourtSpec.classifyPoint(14f, 23f))
        assertEquals(CourtZone.UPPER_LEFT_SERVICE, CourtSpec.classifyPoint(8f, 42f))

        assertEquals(34f / 12f, CourtSpec.netHeightAt(CourtSpec.COURT_CENTER_X), 0.001f)
        assertEquals(3.0f, CourtSpec.netHeightAt(CourtSpec.COURT_LEFT_X), 0.001f)
        assertEquals(3.0f, CourtSpec.netHeightAt(CourtSpec.COURT_RIGHT_X), 0.001f)
    }

    @Test
    fun rulesEngine_doublesStartsAt002AndRotatesProperly() {
        val rules = RulesEngine(
            scoringMode = ScoringMode.SIDE_OUT,
            matchMode = com.picklerally.app.game.MatchMode.DOUBLES,
            pointsToWin = 11,
            winByMargin = 2,
        )
        assertEquals("0 - 0 - 2", rules.formattedScoreCall())
        assertEquals(CourtSide.LOWER, rules.servingSide)

        // Opening team fault causes immediate side-out to Upper Server 1
        val sideOut = rules.resolveFault(CourtSide.LOWER, FaultReason.OUT_OF_BOUNDS)
        assertTrue(sideOut.sideOutOccurred)
        assertNull(sideOut.pointAwardedTo)
        assertEquals(CourtSide.UPPER, rules.servingSide)
        assertEquals("0 - 0 - 1", rules.formattedScoreCall())

        // Upper loses rally as server 1 -> advances to server 2
        val secondServer = rules.resolveFault(CourtSide.UPPER, FaultReason.INTO_NET)
        assertFalse(secondServer.sideOutOccurred)
        assertEquals(2, rules.serverNumberCall)
        assertEquals(CourtSide.UPPER, rules.servingSide)

        // Upper scores a point while serving
        val scored = rules.resolveFault(CourtSide.LOWER, FaultReason.DOUBLE_BOUNCE)
        assertEquals(CourtSide.UPPER, scored.pointAwardedTo)
        assertEquals(1, rules.scoreUpper)
    }

    @Test
    fun rulesEngine_enforcesTwoBounceKitchenVolleyAndWinByTwo() {
        val rules = RulesEngine(scoringMode = ScoringMode.RALLY, pointsToWin = 11, winByMargin = 2)

        assertNull(rules.beginServe(18.5f, 4.9f))
        // Shot 1 is serve: receiver cannot volley
        assertEquals(
            FaultReason.TWO_BOUNCE_VIOLATION,
            rules.validatePlayerHit(CourtSide.UPPER, 10f, 45f, bouncesOnCurrentSide = 0),
        )
        // Return after 1 bounce advances rallyShotCount to 2
        assertNull(rules.validatePlayerHit(CourtSide.UPPER, 10f, 45f, bouncesOnCurrentSide = 1))
        // Shot 2 is return: server still cannot volley third shot
        assertEquals(
            FaultReason.TWO_BOUNCE_VIOLATION,
            rules.validatePlayerHit(CourtSide.LOWER, 18f, 10f, bouncesOnCurrentSide = 0),
        )
        // Third shot after bounce succeeds
        assertNull(rules.validatePlayerHit(CourtSide.LOWER, 18f, 10f, bouncesOnCurrentSide = 1))
        // Fourth shot volley inside kitchen is a kitchen fault
        assertEquals(
            FaultReason.KITCHEN_VOLLEY,
            rules.validatePlayerHit(CourtSide.UPPER, 14f, 31f, bouncesOnCurrentSide = 0),
        )

        // Win-by-2 deuce check
        rules.reset()
        repeat(10) { rules.applyRallyOutcome(CourtSide.LOWER, FaultReason.OUT_OF_BOUNDS) }
        repeat(10) { rules.applyRallyOutcome(CourtSide.UPPER, FaultReason.OUT_OF_BOUNDS) }
        val tenToEleven = rules.applyRallyOutcome(CourtSide.LOWER, FaultReason.OUT_OF_BOUNDS)
        assertNull(tenToEleven.matchWinner)
        val winByTwo = rules.applyRallyOutcome(CourtSide.LOWER, FaultReason.OUT_OF_BOUNDS)
        assertEquals(CourtSide.LOWER, winByTwo.matchWinner)
    }

    @Test
    fun ballPhysics_targetedShotClearsNetAndBouncesInTargetSide() {
        assertEquals(ShotType.DINK, classifySwipe(SwipeVector(0.4f, 1.8f, 0.25f)))
        assertEquals(ShotType.LOB, classifySwipe(SwipeVector(0.1f, 3.8f, 0.22f)))
        assertEquals(ShotType.DRIVE, classifySwipe(SwipeVector(1.5f, 8.0f, 0.14f)))

        val vel = computeTargetShotVelocity(
            fromX = 18f,
            fromY = 7f,
            fromZ = 2.2f,
            targetX = 9f,
            targetY = 44f,
            shotType = ShotType.DRIVE,
        )
        val ball = BallState(
            x = 18f,
            y = 7f,
            z = 2.2f,
            vx = vel.vx,
            vy = vel.vy,
            vz = vel.vz,
            active = true,
            lastHitterSide = CourtSide.LOWER,
        )

        var clearedNet = false
        var hitNet = false
        var firstBounceY = -1f
        for (i in 0 until 120) {
            val ev = stepBallPhysics(ball, CourtSpec.FIXED_TIMESTEP_SEC)
            if (ev.netOutcome == NetCrossOutcome.CLEARED) clearedNet = true
            if (ev.netOutcome == NetCrossOutcome.HIT_NET) hitNet = true
            if (ev.bounced) {
                firstBounceY = ev.bounceY
                break
            }
        }
        assertTrue(clearedNet)
        assertFalse(hitNet)
        assertEquals(44f, firstBounceY, 2.5f)
    }

    @Test
    fun singlePlayerAndDoublesMatch_serveAndAdvanceRally() {
        val solo = SinglePlayerMatch(
            playerChar = CharacterId.RIO,
            botChar = CharacterId.LOLA,
            scoringMode = ScoringMode.RALLY,
            format = MatchFormatConfig(pointsToWin = 11, bestOf = 1, difficulty = BotDifficulty.NORMAL),
        )
        assertEquals(RallyPhase.WAITING_SERVE, solo.rallyPhase)
        val serveOutcome = solo.launchServe(SwipeVector(0f, 5f, 0.15f))
        assertNotNull(serveOutcome.hit)
        assertNull(serveOutcome.fault)
        assertEquals(RallyPhase.RALLY_LIVE, solo.rallyPhase)

        // Step simulation forward and confirm physics advances cleanly without error
        repeat(15) {
            solo.update(CourtSpec.FIXED_TIMESTEP_SEC)
        }
        assertTrue(solo.ball.y > 10f)

        val doubles = DoublesMatch(
            playerChar = CharacterId.RIO,
            partnerChar = CharacterId.DILL,
            upperRightChar = CharacterId.LOLA,
            upperLeftChar = CharacterId.BERT,
        )
        assertEquals("0 - 0 - 2", doubles.rules.formattedScoreCall())
        assertEquals(CourtSide.LOWER, doubles.activeServerSide())
        val doublesServe = doubles.launchServe()
        assertNotNull(doublesServe.hit)
        assertEquals(RallyPhase.RALLY_LIVE, doubles.rallyPhase)
    }

    @Test
    fun onlineMatch1v1_mirrorsCoordinatesAndSerializesSnapshots() {
        assertEquals(10f, OnlineMatch1v1.mirrorX(18f), 0.001f)
        assertEquals(46f, OnlineMatch1v1.mirrorY(10f), 0.001f)

        val host = OnlineMatch1v1(isHost = true, localUid = "host_uid")
        val guest = OnlineMatch1v1(isHost = false, localUid = "guest_uid")

        val guestMove = guest.setLocalMoveTarget(10f, 8f)
        val json = Json { ignoreUnknownKeys = true }
        val decodedMove = json.decodeFromString<PlayerInputSnapshot>(
            json.encodeToString(PlayerInputSnapshot.serializer(), guestMove),
        )
        host.applyRemoteGuestInput(decodedMove)
        assertEquals(OnlineMatch1v1.mirrorX(10f), host.remotePlayer.targetX, 0.01f)
        assertEquals(OnlineMatch1v1.mirrorY(8f), host.remotePlayer.targetY, 0.01f)

        host.triggerLocalSwipe(SwipeVector(0f, 6f, 0.15f))
        val snapshot = host.buildSnapshot()
        val decodedSnap = json.decodeFromString<MatchStateSnapshot>(
            json.encodeToString(MatchStateSnapshot.serializer(), snapshot),
        )
        guest.applyHostSnapshot(decodedSnap)
        assertEquals(RallyPhase.RALLY_LIVE, guest.rallyPhase)
        assertEquals(snapshot.scoreGuest, guest.scoreLocal)
        assertEquals(snapshot.scoreHost, guest.scoreRemote)
    }

    @Test
    fun matchValidator_validatesScoresAndRatingDeltas() {
        val validOnline = MatchValidator.validateMatchPayload(
            SubmittedMatchPayload(
                mode = MatchRecordMode.SINGLES_1V1,
                scoringMode = ScoringMode.RALLY,
                createdByUid = "uid_alpha",
                participants = listOf("uid_alpha", "uid_beta"),
                scoreLower = 11,
                scoreUpper = 8,
                winnerSide = CourtSide.LOWER,
                bestRally = 9,
                playerCharacter = CharacterId.RIO,
                opponentCharacter = CharacterId.LOLA,
            ),
        )
        assertTrue(validOnline.isValid)
        assertTrue(validOnline.isOnline)
        assertEquals(24, validOnline.winnerRatingDelta)
        assertEquals(-12, validOnline.loserRatingDelta)

        val invalidMargin = MatchValidator.validateMatchPayload(
            SubmittedMatchPayload(
                mode = MatchRecordMode.SOLO_1V1,
                scoringMode = ScoringMode.RALLY,
                createdByUid = "uid_alpha",
                participants = listOf("uid_alpha"),
                scoreLower = 11,
                scoreUpper = 10,
                winnerSide = CourtSide.LOWER,
                bestRally = 4,
                playerCharacter = CharacterId.RIO,
                opponentCharacter = CharacterId.LOLA,
            ),
        )
        assertFalse(invalidMargin.isValid)
        assertEquals(1024, MatchValidator.nextPlayerRating(1000, 24))
        assertEquals(800, MatchValidator.nextPlayerRating(805, -20))
    }
}
