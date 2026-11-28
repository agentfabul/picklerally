package com.picklerally.app

import com.picklerally.app.data.DominantHand
import com.picklerally.app.data.NearbyWirePacket
import com.picklerally.app.data.OnlineRoomManager
import com.picklerally.app.data.QueueCandidate
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
import com.picklerally.app.game.PlayerHitState
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
import com.picklerally.app.ui.components.CourtViewportMetrics
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
        assertEquals(1, guest.activeRallyShotCount())
        assertEquals(0, guest.activeBallBouncesOnSide())

        // Guest serve assignment when Host is serving at 0-0: target box is Guest's RIGHT box
        val preServeGuest = OnlineMatch1v1(isHost = false, localUid = "guest_uid")
        val initialSnap = host.let { OnlineMatch1v1(isHost = true, localUid = "h").buildSnapshot() }
        preServeGuest.applyHostSnapshot(initialSnap)
        val guestServeAssign = preServeGuest.currentServeAssignment()
        assertEquals(CourtSide.UPPER, guestServeAssign.servingSide)
        assertEquals(CourtSide.LOWER, guestServeAssign.targetCourtSide)
        assertEquals(ServiceBoxSide.RIGHT, guestServeAssign.targetBox)

        // When ball reaches Guest after 1 bounce, Guest can hit and hitState is READY
        val reachableSnap = decodedSnap.copy(
            ballX = OnlineMatch1v1.mirrorX(guest.localPlayer.targetX),
            ballY = OnlineMatch1v1.mirrorY(guest.localPlayer.targetY),
            ballZ = 2.0f,
            ballActive = true,
            lastHitterSide = "HOST",
            ballBouncesOnSide = 1,
            rallyShotCount = 1,
        )
        guest.applyHostSnapshot(reachableSnap)
        repeat(25) { guest.update(CourtSpec.FIXED_TIMESTEP_SEC) }
        assertTrue(guest.canLocalReachBall())
        assertEquals(PlayerHitState.READY, guest.localPlayerHitState())

        // Before bounce on serve (shot 1), Guest hitState reports WAIT_BOUNCE
        guest.applyHostSnapshot(reachableSnap.copy(ballBouncesOnSide = 0, rallyShotCount = 1))
        assertEquals(PlayerHitState.WAIT_BOUNCE, guest.localPlayerHitState())
    }

    @Test
    fun onlineMatch1v1_handlesWebHostRtdbSnapshotsWithoutExtraFields() {
        val guest = OnlineMatch1v1(isHost = false, localUid = "guest_uid")
        val waitServeSnap = MatchStateSnapshot(
            tick = 1L,
            ballX = 18.5f,
            ballY = 7.2f,
            ballZ = 2.4f,
            ballVx = 0f,
            ballVy = 0f,
            ballVz = 0f,
            ballActive = false,
            hostX = 18.5f,
            hostY = 4.9f,
            guestX = 9.5f,
            guestY = 49.1f,
            hostSwingTimer = 0f,
            guestSwingTimer = 0f,
            scoreHost = 0,
            scoreGuest = 0,
            servingSide = "HOST",
            rallyShotCount = 0,
            rallyHits = 0,
            lastEvent = null,
            matchWinner = null,
            publishedAtMs = 1000L,
            lastHitterSide = null,
            ballBouncesOnSide = 0,
        )
        guest.applyHostSnapshot(waitServeSnap)
        assertEquals(RallyPhase.WAITING_SERVE, guest.rallyPhase)
        assertEquals(OnlineMatch1v1.mirrorX(9.5f), guest.localPlayer.x, 0.01f)
        assertEquals(OnlineMatch1v1.mirrorY(49.1f), guest.localPlayer.y, 0.01f)

        // Web host sends active ball coming toward Guest (host ballVy > 0 -> not hit by guest)
        // First snapshot descending toward ground within Guest's reach on guest's court half
        val descendingSnap = waitServeSnap.copy(
            tick = 20L,
            ballX = 9.5f,
            ballY = 46.5f,
            ballZ = 0.2f,
            ballVy = 28.0f,
            ballVz = -12.0f,
            ballActive = true,
            rallyShotCount = 1,
        )
        guest.applyHostSnapshot(descendingSnap)
        repeat(25) { guest.update(CourtSpec.FIXED_TIMESTEP_SEC) }
        assertEquals(0, guest.activeBallBouncesOnSide())
        assertEquals(PlayerHitState.WAIT_BOUNCE, guest.localPlayerHitState())

        // Next snapshot after bounce (ballVz flips positive while on guest half)
        val bouncedSnap = descendingSnap.copy(
            tick = 24L,
            ballY = 46.0f,
            ballZ = 1.6f,
            ballVz = 10.5f,
        )
        guest.applyHostSnapshot(bouncedSnap)
        repeat(25) { guest.update(CourtSpec.FIXED_TIMESTEP_SEC) }
        assertEquals(1, guest.activeBallBouncesOnSide())
        assertEquals(PlayerHitState.READY, guest.localPlayerHitState())

        // Point pause callout from Web host triggers POINT_PAUSE
        guest.applyHostSnapshot(
            waitServeSnap.copy(
                scoreHost = 1,
                lastEvent = "Kitchen fault!",
            ),
        )
        assertEquals(RallyPhase.POINT_PAUSE, guest.rallyPhase)
    }

    @Test
    fun onlineRoomManager_normalizesCodesAndTruncatesProfileNames() {
        assertEquals("PKL-427", OnlineRoomManager.normalizeRoomCode("427"))
        assertEquals("PKL-427", OnlineRoomManager.normalizeRoomCode(" pkl-427 "))
        assertEquals("Alex", OnlineRoomManager.sanitizeProfileName("  Alex  ", CharacterId.RIO))
        assertEquals("FourteenCharac", OnlineRoomManager.sanitizeProfileName("FourteenCharacterLongName", CharacterId.RIO))
        assertEquals("Gus", OnlineRoomManager.sanitizeProfileName("   ", CharacterId.GUS))
    }

    @Test
    fun profileNames_preserveHeroCharacterIdAndSerializeInNearbyPacket() {
        val match = OnlineMatch1v1(
            isHost = true,
            localUid = "host_uid",
            localChar = CharacterId.GUS,
            remoteChar = CharacterId.BERT,
            localName = "John",
            remoteName = "Alex",
        )
        assertEquals(CharacterId.GUS, match.localPlayer.characterId)
        assertEquals("Gus", match.localPlayer.characterId.displayName)
        assertEquals("John", match.localPlayer.displayName)
        assertEquals(CharacterId.BERT, match.remotePlayer.characterId)
        assertEquals("Bert", match.remotePlayer.characterId.displayName)
        assertEquals("Alex", match.remotePlayer.displayName)

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val packet = NearbyWirePacket(
            type = "hello",
            charId = CharacterId.GUS.name,
            playerName = "John",
        )
        val decoded = json.decodeFromString<NearbyWirePacket>(
            json.encodeToString(NearbyWirePacket.serializer(), packet),
        )
        assertEquals("hello", decoded.type)
        assertEquals("GUS", decoded.charId)
        assertEquals("John", decoded.playerName)
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

    @Test
    fun onlineRoomManager_selectsAndSortsQueueCandidates() {
        val nowMs = 1_000_000L
        val myUid = "me_uid"
        val myRating = 1050

        val candidates = listOf(
            // Self host -> excluded
            QueueCandidate("PKL-101", host = myUid, name = "Me", characterId = "RIO", rating = 1050, createdAtMs = nowMs - 5_000L),
            // Already claimed -> excluded
            QueueCandidate("PKL-102", host = "u2", name = "Claimed", characterId = "LOLA", rating = 1050, createdAtMs = nowMs - 5_000L, claimedBy = "other"),
            // Older than 90s -> excluded
            QueueCandidate("PKL-103", host = "u3", name = "Stale", characterId = "BERT", rating = 1050, createdAtMs = nowMs - 95_000L),
            // Invalid code -> excluded
            QueueCandidate("BAD-104", host = "u4", name = "Bad", characterId = "RIO", rating = 1050, createdAtMs = nowMs - 5_000L),
            // Valid, rating diff 30, newer
            QueueCandidate("PKL-201", host = "u5", name = "FarNew", characterId = "MAYA", rating = 1080, createdAtMs = nowMs - 2_000L),
            // Valid, rating diff 10, newer
            QueueCandidate("PKL-202", host = "u6", name = "CloseNew", characterId = "KAI", rating = 1040, createdAtMs = nowMs - 10_000L),
            // Valid, rating diff 10, older (should come before PKL-202)
            QueueCandidate("PKL-203", host = "u7", name = "CloseOld", characterId = "ZOE", rating = 1060, createdAtMs = nowMs - 40_000L),
            // Valid, exactly at 90s TTL boundary -> still included, rating diff 100
            QueueCandidate("PKL-204", host = "u8", name = "EdgeTTL", characterId = "GUS", rating = 1150, createdAtMs = nowMs - 90_000L),
        )

        val selected = OnlineRoomManager.selectAndSortQueueCandidates(
            candidates = candidates,
            myUid = myUid,
            myRating = myRating,
            nowMs = nowMs,
        )

        assertEquals(
            listOf("PKL-203", "PKL-202", "PKL-201", "PKL-204"),
            selected.map { it.code },
        )
    }

    @Test
    fun courtViewportMetrics_respectsInsetsAndPreservesCourtProportions() {
        val w = 1080f
        val h = 2340f
        val topInset = 190f
        val bottomInset = 75f
        val leftInset = 16f
        val rightInset = 16f

        val metrics = CourtViewportMetrics.compute(
            canvasWidth = w,
            canvasHeight = h,
            topInsetPx = topInset,
            bottomInsetPx = bottomInset,
            leftInsetPx = leftInset,
            rightInsetPx = rightInset,
        )

        val topLeft = metrics.worldToScreen(CourtSpec.COURT_LEFT_X, CourtSpec.COURT_TOP_Y)
        val bottomRight = metrics.worldToScreen(CourtSpec.COURT_RIGHT_X, CourtSpec.COURT_BOTTOM_Y)

        assertTrue(topLeft.x >= leftInset)
        assertTrue(bottomRight.x <= w - rightInset)
        // worldToScreen flips Y: COURT_TOP_Y (50f) is smaller screen Y than COURT_BOTTOM_Y (6f)
        assertTrue(topLeft.y >= topInset)
        assertTrue(bottomRight.y <= h - bottomInset)

        val screenCourtWidth = bottomRight.x - topLeft.x
        val screenCourtHeight = bottomRight.y - topLeft.y
        assertEquals(CourtSpec.COURT_WIDTH_FT / CourtSpec.COURT_LENGTH_FT, screenCourtWidth / screenCourtHeight, 0.002f)

        val (roundTripX, roundTripY) = metrics.screenToWorld(topLeft.x, topLeft.y)
        assertEquals(CourtSpec.COURT_LEFT_X, roundTripX, 0.02f)
        assertEquals(CourtSpec.COURT_TOP_Y, roundTripY, 0.02f)
    }

    @Test
    fun onlineMatch1v1_hostAutoSwingsForGuestAndGuestPredictsLocalSwing() {
        val host = OnlineMatch1v1(isHost = true, localUid = "host_uid")
        host.triggerLocalSwipe(SwipeVector(0f, 5f, 0.15f))
        assertEquals(RallyPhase.RALLY_LIVE, host.rallyPhase)

        // Position ball just bounced on guest's (upper) side, within generous remote reach (4.7ft < 5.15f)
        host.remotePlayer.x = 10f
        host.remotePlayer.y = 44f
        host.remotePlayer.targetX = 10f
        host.remotePlayer.targetY = 44f
        host.ball.x = 10f
        host.ball.y = 39.4f // 4.6ft away -> > 4.3f local reach, inside 5.15f remote reach, moving away if vy < 0
        host.ball.z = 2.2f
        host.ball.vx = 0f
        host.ball.vy = -8f
        host.ball.vz = 3f
        host.ball.bouncesOnCurrentSide = 1
        host.ball.lastHitterSide = CourtSide.LOWER
        host.ball.active = true

        val res = host.update(CourtSpec.FIXED_TIMESTEP_SEC * 2f)
        assertNotNull(res.hitFeedback)
        assertEquals(CourtSide.UPPER, res.hitFeedback?.hitterSide)
        assertEquals(CourtSide.UPPER, host.ball.lastHitterSide)
        assertTrue(host.remotePlayer.swingTimer > 0f)

        // Guest local prediction: immediate swing animation before host confirms
        val guest = OnlineMatch1v1(isHost = false, localUid = "guest_uid")
        val readySnap = host.buildSnapshot().copy(
            ballX = OnlineMatch1v1.mirrorX(guest.localPlayer.x),
            ballY = OnlineMatch1v1.mirrorY(guest.localPlayer.y + 0.8f),
            ballZ = 2.0f,
            ballVx = 0f,
            ballVy = 12f,
            ballVz = 2f,
            ballActive = true,
            lastHitterSide = "HOST",
            ballBouncesOnSide = 1,
            rallyShotCount = 1,
            guestSwingTimer = 0f,
        )
        guest.applyHostSnapshot(readySnap)
        repeat(25) { guest.update(CourtSpec.FIXED_TIMESTEP_SEC) }
        assertTrue(guest.shouldGuestAutoSwingLocal())
        val swipeRes = guest.triggerLocalSwipe(SwipeVector(0f, 6f, 0.15f))
        assertNotNull(swipeRes.guestInput?.swingType)
        assertTrue(guest.localPlayer.swingTimer > 0f)
    }

    @Test
    fun joystickInput_movesProportionallyAndStopsImmediatelyOnRelease() {
        val fullPushMatch = SinglePlayerMatch()
        val halfPushMatch = SinglePlayerMatch()
        fullPushMatch.launchServe(SwipeVector(0f, 5f, 0.15f))
        halfPushMatch.launchServe(SwipeVector(0f, 5f, 0.15f))
        val startX = fullPushMatch.player.x
        val startY = fullPushMatch.player.y

        fullPushMatch.setJoystickInput(0f, 1f)
        halfPushMatch.setJoystickInput(0f, 0.5f)
        repeat(12) {
            fullPushMatch.update(CourtSpec.FIXED_TIMESTEP_SEC)
            halfPushMatch.update(CourtSpec.FIXED_TIMESTEP_SEC)
        }

        val fullDeltaY = fullPushMatch.player.y - startY
        val halfDeltaY = halfPushMatch.player.y - startY
        assertEquals(startX, fullPushMatch.player.x, 0.001f)
        assertTrue(fullDeltaY > 2.0f)
        assertEquals(fullDeltaY * 0.5f, halfDeltaY, 0.25f)

        // Releasing joystick locks target to current position and stops motion
        fullPushMatch.setJoystickInput(0f, 0f)
        val stoppedY = fullPushMatch.player.y
        repeat(12) { fullPushMatch.update(CourtSpec.FIXED_TIMESTEP_SEC) }
        assertEquals(stoppedY, fullPushMatch.player.y, 0.001f)

        // Online guest emits immediate stop packet on joystick release and parses DominantHand safely
        val guest = OnlineMatch1v1(isHost = false, localUid = "guest_uid")
        assertNull(guest.setJoystickInput(0.7f, 0.7f))
        repeat(6) { guest.update(CourtSpec.FIXED_TIMESTEP_SEC) }
        val releaseSnap = guest.setJoystickInput(0f, 0f)
        assertNotNull(releaseSnap)
        assertEquals(guest.localPlayer.x, releaseSnap!!.targetX, 0.01f)
        assertEquals(guest.localPlayer.y, releaseSnap.targetY, 0.01f)
        assertEquals(DominantHand.LEFT, DominantHand.fromStringOrDefault("LEFT"))
        assertEquals(DominantHand.RIGHT, DominantHand.fromStringOrDefault("unknown"))
    }
}
