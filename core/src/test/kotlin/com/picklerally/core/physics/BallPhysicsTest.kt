package com.picklerally.core.physics

import com.picklerally.core.court.CourtSide
import com.picklerally.core.court.CourtSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BallPhysicsTest {

    @Test
    fun `fixed timestep simulation is deterministic across identical runs`() {
        val a = BallState(x = 14f, y = 10f, z = 2.5f, vx = 2.1f, vy = 22f, vz = 14f, active = true)
        val b = a.copy()

        repeat(90) {
            BallPhysics.step(a, CourtSpec.FIXED_TIMESTEP_SEC)
            BallPhysics.step(b, CourtSpec.FIXED_TIMESTEP_SEC)
        }

        assertEquals(a.x, b.x, 1e-6f)
        assertEquals(a.y, b.y, 1e-6f)
        assertEquals(a.z, b.z, 1e-6f)
        assertEquals(a.vz, b.vz, 1e-6f)
    }

    @Test
    fun `gravity pulls ball down and bounce loses energy`() {
        val ball = BallState(x = 14f, y = 12f, z = 4.0f, vx = 0f, vy = 0f, vz = 0f, active = true)
        var bounced = false
        var peakAfterBounce = 0f

        repeat(120) {
            val ev = BallPhysics.step(ball, CourtSpec.FIXED_TIMESTEP_SEC)
            if (ev.bounced) bounced = true
            if (bounced && ball.z > peakAfterBounce) peakAfterBounce = ball.z
        }

        assertTrue(bounced, "Ball should hit ground under gravity")
        assertTrue(peakAfterBounce < 4.0f && peakAfterBounce > 1.5f, "Rebound height should show restitution energy loss")
    }

    @Test
    fun `net check detects low ball collision and passes high clearance`() {
        val lowBall = BallState(x = 14f, y = CourtSpec.NET_Y - 0.3f, z = 1.8f, vx = 0f, vy = 25f, vz = 0f, active = true)
        val lowEvent = BallPhysics.step(lowBall, CourtSpec.FIXED_TIMESTEP_SEC)
        assertEquals(NetCrossOutcome.HIT_NET, lowEvent.netOutcome)

        val highBall = BallState(x = 14f, y = CourtSpec.NET_Y - 0.3f, z = 4.2f, vx = 0f, vy = 25f, vz = 0f, active = true)
        val highEvent = BallPhysics.step(highBall, CourtSpec.FIXED_TIMESTEP_SEC)
        assertEquals(NetCrossOutcome.CLEARED, highEvent.netOutcome)
    }

    @Test
    fun `swipe classifier distinguishes dink, drive, and lob`() {
        val dink = SwipeVector(dxFt = 0.6f, dyFt = 1.6f, durationSec = 0.25f)
        val drive = SwipeVector(dxFt = 1.8f, dyFt = 6.2f, durationSec = 0.16f)
        val lob = SwipeVector(dxFt = 0.2f, dyFt = 3.8f, durationSec = 0.20f)

        assertEquals(ShotType.DINK, BallPhysics.classifySwipe(dink))
        assertEquals(ShotType.DRIVE, BallPhysics.classifySwipe(drive))
        assertEquals(ShotType.LOB, BallPhysics.classifySwipe(lob))
    }

    @Test
    fun `computeShotVelocity launches toward requested side and scales sprite with z`() {
        val (vx, vy, vz) = BallPhysics.computeShotVelocity(
            fromX = 14f,
            fromY = 10f,
            swipe = SwipeVector(1.0f, 5.0f, 0.18f),
            shotType = ShotType.DRIVE,
            targetSide = CourtSide.UPPER,
            timingAccuracy = 0.9f
        )

        assertTrue(vy > 0f, "Upper target shot must travel in +Y")
        assertTrue(vz > 0f, "Shot must have upward launch arc")
        assertTrue(BallPhysics.spriteScaleForHeight(6f) > BallPhysics.spriteScaleForHeight(0f))
        assertTrue(vx.isFinite())
    }

    @Test
    fun `bot returns clear net and land in bounds at least 80 percent across 200 simulations`() {
        val rng = kotlin.random.Random(1337)
        var landedIn = 0

        repeat(200) {
            val fromX = CourtSpec.COURT_LEFT_X + 1.5f + rng.nextFloat() * (CourtSpec.COURT_WIDTH_FT - 3.0f)
            val fromY = CourtSpec.UPPER_NVZ_Y + 0.5f + rng.nextFloat() * 14.0f
            val fromZ = 0.6f + rng.nextFloat() * 3.6f
            val playerX = CourtSpec.COURT_LEFT_X + 2.0f + rng.nextFloat() * (CourtSpec.COURT_WIDTH_FT - 4.0f)

            val shot = BallPhysics.computeBotReturnShot(
                fromX = fromX,
                fromY = fromY,
                fromZ = fromZ,
                hitterY = fromY,
                targetSide = CourtSide.LOWER,
                opponentXs = listOf(playerX),
                difficulty = BotDifficulty.NORMAL,
                rng = rng
            )

            val simBall = BallState(
                x = fromX,
                y = fromY,
                z = fromZ,
                vx = shot.vx,
                vy = shot.vy,
                vz = shot.vz,
                active = true,
                lastHitterSide = CourtSide.UPPER
            )

            var shotIn = false
            for (step in 0 until 180) {
                val ev = BallPhysics.step(simBall, CourtSpec.FIXED_TIMESTEP_SEC)
                if (ev.netOutcome == NetCrossOutcome.HIT_NET) break
                if (ev.bounced) {
                    shotIn = simBall.crossedNetSinceHit &&
                        simBall.y < CourtSpec.NET_Y &&
                        CourtSpec.isInBounds(simBall.x, simBall.y)
                    break
                }
                if (simBall.x < -2f || simBall.x > CourtSpec.WORLD_WIDTH_FT + 2f ||
                    simBall.y < -3f || simBall.y > CourtSpec.WORLD_HEIGHT_FT + 3f
                ) {
                    break
                }
            }
            if (shotIn) landedIn += 1
        }

        assertTrue(landedIn >= 160, "Expected >= 80% (160/200) bot returns in bounds, got $landedIn")
    }

    @Test
    fun `bot shot landing in middle of player court is in and wins point or side-out when unreturned`() {
        fun simulateUnreturned(scoringMode: com.picklerally.core.rules.ScoringMode): Pair<com.picklerally.core.sim.SinglePlayerMatch, com.picklerally.core.rules.PointResolution?> {
            val match = com.picklerally.core.sim.SinglePlayerMatch(
                scoringMode = scoringMode,
                rng = kotlin.random.Random(42)
            )
            match.launchServe()
            assertEquals(0, match.rallyHits, "Rally count should remain 0 before 3rd shot")

            for (i in 0 until 180) {
                match.update(CourtSpec.FIXED_TIMESTEP_SEC)
                if (match.ball.lastHitterSide == CourtSide.UPPER && match.rules.rallyShotCount == 2) {
                    val v = BallPhysics.computeTargetShotVelocity(
                        fromX = match.ball.x,
                        fromY = match.ball.y,
                        fromZ = match.ball.z,
                        targetX = 14f,
                        targetY = 14f,
                        shotType = ShotType.DRIVE,
                        minNetClearanceFt = 0.5f
                    )
                    match.ball.vx = v.first
                    match.ball.vy = v.second
                    match.ball.vz = v.third
                    break
                }
            }
            assertEquals(0, match.rallyHits, "Rally count should remain 0 after 2nd shot until 3rd shot is hit")

            match.setPlayerMoveTarget(2f, 2f)
            var resolved: com.picklerally.core.rules.PointResolution? = null
            for (i in 0 until 360) {
                val outs = match.update(CourtSpec.FIXED_TIMESTEP_SEC)
                resolved = outs.firstNotNullOfOrNull { it.pointResolution }
                if (resolved != null) break
            }
            return Pair(match, resolved)
        }

        val (rallyMatch, rallyRes) = simulateUnreturned(com.picklerally.core.rules.ScoringMode.RALLY)
        assertEquals(null, rallyMatch.rules.validateFirstBounce(CourtSpec.COURT_LEFT_X, CourtSpec.COURT_BOTTOM_Y, CourtSide.UPPER))
        assertTrue(rallyRes != null && rallyRes.faultReason != com.picklerally.core.rules.FaultReason.OUT_OF_BOUNDS)
        assertEquals(CourtSide.UPPER, rallyRes?.rallyWinner)
        assertEquals(CourtSide.UPPER, rallyRes?.pointAwardedTo)
        assertEquals(1, rallyMatch.rules.scoreUpper)

        val (sideOutMatch, sideOutRes) = simulateUnreturned(com.picklerally.core.rules.ScoringMode.SIDE_OUT)
        assertEquals(CourtSide.UPPER, sideOutRes?.rallyWinner)
        assertEquals(true, sideOutRes?.sideOutOccurred)
        assertEquals(CourtSide.UPPER, sideOutMatch.rules.servingSide)
    }
}
