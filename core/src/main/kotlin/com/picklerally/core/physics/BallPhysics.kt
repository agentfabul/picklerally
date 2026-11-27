package com.picklerally.core.physics

import com.picklerally.core.court.CourtSide
import com.picklerally.core.court.CourtSpec
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.random.Random

enum class BotDifficulty(val errorChance: Float) {
    EASY(0.24f),
    NORMAL(0.12f),
    HARD(0.05f)
}

data class BotShotSolution(
    val shotType: ShotType,
    val vx: Float,
    val vy: Float,
    val vz: Float,
    val targetX: Float,
    val targetY: Float
)

enum class ShotType(val label: String) {
    DINK("Dink"),
    DRIVE("Drive"),
    LOB("Lob"),
    SERVE("Serve")
}

enum class NetCrossOutcome {
    NONE,
    CLEARED,
    HIT_NET
}

data class BallState(
    var x: Float = CourtSpec.COURT_CENTER_X,
    var y: Float = CourtSpec.COURT_BOTTOM_Y + 2f,
    var z: Float = 2.5f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var active: Boolean = false,
    var bouncesOnCurrentSide: Int = 0,
    var lastHitterSide: CourtSide? = null,
    var crossedNetSinceHit: Boolean = false
)

data class StepEvents(
    val bounced: Boolean = false,
    val bounceZSpeed: Float = 0f,
    val bounceX: Float = 0f,
    val bounceY: Float = 0f,
    val netOutcome: NetCrossOutcome = NetCrossOutcome.NONE
)

data class SwipeVector(
    val dxFt: Float,
    val dyFt: Float,
    val durationSec: Float
) {
    val distanceFt: Float get() = hypot(dxFt, dyFt)
    val speedFtPerSec: Float get() = distanceFt / max(durationSec, 0.04f)
}

object BallPhysics {
    const val GRAVITY_FT_S2 = -34.0f
    const val BOUNCE_RESTITUTION = 0.76f
    const val HORIZONTAL_AIR_DRAG = 0.14f
    const val BOUNCE_HORIZONTAL_FRICTION = 0.92f
    const val BALL_RADIUS_FT = 0.16f

    /**
     * Advance ball simulation by one fixed timestep (1/60s).
     * Checks interpolated net crossing at CourtSpec.NET_Y and ground bounce at z = 0.
     */
    fun step(ball: BallState, dt: Float = CourtSpec.FIXED_TIMESTEP_SEC): StepEvents {
        if (!ball.active) return StepEvents()

        val prevX = ball.x
        val prevY = ball.y
        val prevZ = ball.z

        // Exponential-style linear air drag on horizontal velocity
        val dragFactor = max(0f, 1f - HORIZONTAL_AIR_DRAG * dt)
        ball.vx *= dragFactor
        ball.vy *= dragFactor

        // Semi-implicit Euler integration with gravity on Z
        ball.vz += GRAVITY_FT_S2 * dt
        ball.x += ball.vx * dt
        ball.y += ball.vy * dt
        ball.z += ball.vz * dt

        // Check if ball crossed the net line during this substep
        var netOutcome = NetCrossOutcome.NONE
        val crossedNet =
            (prevY < CourtSpec.NET_Y && ball.y >= CourtSpec.NET_Y) ||
                (prevY > CourtSpec.NET_Y && ball.y <= CourtSpec.NET_Y)

        if (crossedNet) {
            val dy = ball.y - prevY
            val t = if (abs(dy) > 1e-5f) ((CourtSpec.NET_Y - prevY) / dy).coerceIn(0f, 1f) else 0.5f
            val crossX = prevX + (ball.x - prevX) * t
            val crossZ = prevZ + (ball.z - prevZ) * t
            val requiredZ = CourtSpec.netHeightAt(crossX) + BALL_RADIUS_FT * 0.6f

            val withinPosts = crossX in
                (CourtSpec.COURT_LEFT_X - CourtSpec.NET_POST_OFFSET_FT)..(CourtSpec.COURT_RIGHT_X + CourtSpec.NET_POST_OFFSET_FT)

            if (withinPosts && crossZ < requiredZ) {
                netOutcome = NetCrossOutcome.HIT_NET
                ball.x = crossX
                ball.y = CourtSpec.NET_Y - sign(ball.vy) * 0.22f
                ball.z = crossZ.coerceAtLeast(0.2f)
                ball.vx *= 0.25f
                ball.vy = -ball.vy * 0.22f
                ball.vz = -1.5f
            } else {
                netOutcome = NetCrossOutcome.CLEARED
                ball.crossedNetSinceHit = true
                ball.bouncesOnCurrentSide = 0
            }
        }

        // Ground bounce check at z <= 0
        var bounced = false
        var bounceZSpeed = 0f
        var bounceX = ball.x
        var bounceY = ball.y
        val rawZ = ball.z
        if (rawZ <= 0f) {
            val denom = prevZ - rawZ
            val tBounce = if (prevZ > 0f && denom > 1e-5f) (prevZ / denom).coerceIn(0f, 1f) else 1f
            bounceX = prevX + (ball.x - prevX) * tBounce
            bounceY = prevY + (ball.y - prevY) * tBounce

            bounceZSpeed = abs(ball.vz)
            ball.z = 0f
            if (bounceZSpeed > 1.2f) {
                ball.vz = bounceZSpeed * BOUNCE_RESTITUTION
                ball.vx *= BOUNCE_HORIZONTAL_FRICTION
                ball.vy *= BOUNCE_HORIZONTAL_FRICTION
            } else {
                ball.vz = 0f
                ball.vx *= 0.85f
                ball.vy *= 0.85f
            }
            ball.bouncesOnCurrentSide += 1
            bounced = true
        }

        return StepEvents(
            bounced = bounced,
            bounceZSpeed = bounceZSpeed,
            bounceX = bounceX,
            bounceY = bounceY,
            netOutcome = netOutcome
        )
    }

    /**
     * Classify a player's swipe into DINK, DRIVE, or LOB based on distance, speed, and vertical lift.
     */
    fun classifySwipe(swipe: SwipeVector): ShotType {
        val dist = swipe.distanceFt
        val speed = swipe.speedFtPerSec
        val forwardRatio = if (dist > 0.01f) abs(swipe.dyFt) / dist else 0f

        return when {
            // Upward flick with moderate-to-high speed and predominantly vertical motion
            swipe.dyFt > 2.2f && forwardRatio > 0.72f && speed in 10f..27f && dist < 5.2f -> ShotType.LOB
            // Short, gentle swipe
            dist < 2.6f || speed < 11.5f -> ShotType.DINK
            // Fast, long swipe
            else -> ShotType.DRIVE
        }
    }

    fun horizontalDragIntegral(flightTimeSec: Float): Float {
        if (HORIZONTAL_AIR_DRAG <= 1e-5f) return flightTimeSec
        return (1f - exp(-HORIZONTAL_AIR_DRAG * flightTimeSec)) / HORIZONTAL_AIR_DRAG
    }

    private fun timeToCrossNet(fromY: Float, targetY: Float, flightTimeSec: Float): Pair<Float, Float> {
        val dyTotal = targetY - fromY
        val dyNet = CourtSpec.NET_Y - fromY
        if (abs(dyTotal) < 1e-4f || dyTotal * dyNet <= 0f || abs(dyNet) >= abs(dyTotal)) {
            return Pair(flightTimeSec * 0.5f, 0.5f)
        }
        val fracNet = (dyNet / dyTotal).coerceIn(0.05f, 0.95f)
        if (HORIZONTAL_AIR_DRAG <= 1e-5f) {
            return Pair(flightTimeSec * fracNet, fracNet)
        }
        val expNet = (1f - fracNet * (1f - exp(-HORIZONTAL_AIR_DRAG * flightTimeSec))).coerceAtLeast(0.02f)
        return Pair(-ln(expNet) / HORIZONTAL_AIR_DRAG, fracNet)
    }

    /**
     * Solve launch velocity (vx, vy, vz) so the ball travels from (fromX, fromY, fromZ)
     * to land at (targetX, targetY, z=0) while guaranteeing net clearance when minNetClearanceFt >= 0.
     */
    fun computeTargetShotVelocity(
        fromX: Float,
        fromY: Float,
        fromZ: Float,
        targetX: Float,
        targetY: Float,
        shotType: ShotType,
        minNetClearanceFt: Float = when (shotType) {
            ShotType.LOB -> 1.8f
            ShotType.DINK -> 0.65f
            else -> 0.50f
        }
    ): Triple<Float, Float, Float> {
        val z0 = max(0.25f, fromZ)
        val dist = hypot(targetX - fromX, targetY - fromY)

        var flightTime = when (shotType) {
            ShotType.DINK -> (dist / 24f).coerceIn(0.62f, 0.84f)
            ShotType.LOB -> (dist / 27f).coerceIn(1.24f, 1.58f)
            ShotType.SERVE -> 0.94f
            ShotType.DRIVE -> (dist / 46f).coerceIn(0.58f, 0.84f)
        }

        var vz = (-0.5f * GRAVITY_FT_S2 * flightTime * flightTime - z0) / flightTime

        for (i in 0 until 36) {
            val (tNet, fracNet) = timeToCrossNet(fromY, targetY, flightTime)
            val netX = fromX + (targetX - fromX) * fracNet
            val baseRequiredNetZ = CourtSpec.netHeightAt(netX) + BALL_RADIUS_FT * 0.6f
            val zAtNet = z0 + vz * tNet + 0.5f * GRAVITY_FT_S2 * tNet * tNet

            if (minNetClearanceFt < 0f) {
                vz = (baseRequiredNetZ - 0.35f - z0 - 0.5f * GRAVITY_FT_S2 * tNet * tNet) / max(0.15f, tNet)
                break
            }

            if (zAtNet >= baseRequiredNetZ + minNetClearanceFt || flightTime >= 2.2f) {
                break
            }
            flightTime += 0.04f
            vz = (-0.5f * GRAVITY_FT_S2 * flightTime * flightTime - z0) / flightTime
        }

        val invIntegral = 1f / horizontalDragIntegral(flightTime)
        val vx = (targetX - fromX) * invIntegral
        val vy = (targetY - fromY) * invIntegral
        return Triple(vx, vy, vz)
    }

    fun computeBotReturnShot(
        fromX: Float,
        fromY: Float,
        fromZ: Float,
        hitterY: Float,
        targetSide: CourtSide,
        opponentXs: List<Float>,
        difficulty: BotDifficulty = BotDifficulty.NORMAL,
        rng: Random = Random.Default
    ): BotShotSolution {
        val minX = CourtSpec.COURT_LEFT_X + 2.0f
        val maxX = CourtSpec.COURT_RIGHT_X - 2.0f
        val minY = if (targetSide == CourtSide.LOWER) CourtSpec.COURT_BOTTOM_Y + 2.2f else CourtSpec.NET_Y + 2.6f
        val maxY = if (targetSide == CourtSide.LOWER) CourtSpec.NET_Y - 2.6f else CourtSpec.COURT_TOP_Y - 2.2f

        val roll = rng.nextFloat()
        val nearKitchen = abs(hitterY - CourtSpec.NET_Y) < 9.5f
        val shotType = when {
            nearKitchen && roll < 0.38f -> ShotType.DINK
            roll < 0.85f -> ShotType.DRIVE
            else -> ShotType.LOB
        }

        val oppX = if (opponentXs.isEmpty()) {
            CourtSpec.COURT_CENTER_X
        } else {
            opponentXs[rng.nextInt(opponentXs.size)]
        }
        val preferredX = if (oppX >= CourtSpec.COURT_CENTER_X) {
            CourtSpec.COURT_CENTER_X - 3.6f - rng.nextFloat() * 3.0f
        } else {
            CourtSpec.COURT_CENTER_X + 3.6f + rng.nextFloat() * 3.0f
        }
        var targetX = preferredX.coerceIn(minX, maxX)

        var targetY = when (shotType) {
            ShotType.DINK -> {
                val depth = 3.2f + rng.nextFloat() * 2.6f
                if (targetSide == CourtSide.LOWER) CourtSpec.NET_Y - depth else CourtSpec.NET_Y + depth
            }
            ShotType.LOB -> {
                val margin = 2.6f + rng.nextFloat() * 2.8f
                if (targetSide == CourtSide.LOWER) CourtSpec.COURT_BOTTOM_Y + margin else CourtSpec.COURT_TOP_Y - margin
            }
            else -> {
                val margin = 3.0f + rng.nextFloat() * 6.5f
                if (targetSide == CourtSide.LOWER) CourtSpec.COURT_BOTTOM_Y + margin else CourtSpec.COURT_TOP_Y - margin
            }
        }.coerceIn(minY, maxY)

        var minClearance = when (shotType) {
            ShotType.LOB -> 1.8f
            ShotType.DINK -> 0.65f
            else -> 0.50f
        }

        if (rng.nextFloat() < difficulty.errorChance) {
            val errRoll = rng.nextFloat()
            if (errRoll < 0.45f) {
                minClearance = -0.35f
            } else if (errRoll < 0.75f) {
                targetY = if (targetSide == CourtSide.LOWER) {
                    CourtSpec.COURT_BOTTOM_Y - (0.8f + rng.nextFloat() * 1.4f)
                } else {
                    CourtSpec.COURT_TOP_Y + (0.8f + rng.nextFloat() * 1.4f)
                }
            } else {
                targetX = if (targetX < CourtSpec.COURT_CENTER_X) {
                    CourtSpec.COURT_LEFT_X - (0.8f + rng.nextFloat() * 1.2f)
                } else {
                    CourtSpec.COURT_RIGHT_X + (0.8f + rng.nextFloat() * 1.2f)
                }
            }
        }

        val (vx, vy, vz) = computeTargetShotVelocity(
            fromX = fromX,
            fromY = fromY,
            fromZ = fromZ,
            targetX = targetX,
            targetY = targetY,
            shotType = shotType,
            minNetClearanceFt = minClearance
        )
        return BotShotSolution(shotType, vx, vy, vz, targetX, targetY)
    }

    /**
     * Compute launch velocity (vx, vy, vz) aimed along the player's swipe direction toward the opponent's half.
     * Timing quality (0..1) narrows lateral & depth error while guaranteeing net clearance.
     */
    fun computeShotVelocity(
        fromX: Float,
        fromY: Float,
        swipe: SwipeVector,
        shotType: ShotType,
        targetSide: CourtSide,
        timingAccuracy: Float,
        scatterSeed: Float = 0f,
        fromZ: Float = 2.0f
    ): Triple<Float, Float, Float> {
        val rawDx = swipe.dxFt
        val rawDy = max(0.75f, abs(swipe.dyFt))
        val aimRatio = (rawDx / rawDy).coerceIn(-0.90f, 0.90f)

        val errorScale = (1f - timingAccuracy.coerceIn(0f, 1f))
        val scatterX = scatterSeed * 2.2f * errorScale
        val scatterDepth = scatterSeed * 1.8f * errorScale

        val targetY = when (shotType) {
            ShotType.DINK -> if (targetSide == CourtSide.UPPER) {
                CourtSpec.NET_Y + 4.4f + scatterDepth * 0.4f
            } else {
                CourtSpec.NET_Y - 4.4f - scatterDepth * 0.4f
            }
            ShotType.DRIVE -> if (targetSide == CourtSide.UPPER) {
                CourtSpec.COURT_TOP_Y - 4.2f + scatterDepth
            } else {
                CourtSpec.COURT_BOTTOM_Y + 4.2f - scatterDepth
            }
            ShotType.LOB -> if (targetSide == CourtSide.UPPER) {
                CourtSpec.COURT_TOP_Y - 3.4f + scatterDepth * 0.6f
            } else {
                CourtSpec.COURT_BOTTOM_Y + 3.4f - scatterDepth * 0.6f
            }
            ShotType.SERVE -> if (targetSide == CourtSide.UPPER) {
                CourtSpec.UPPER_NVZ_Y + 8.5f
            } else {
                CourtSpec.LOWER_NVZ_Y - 8.5f
            }
        }

        val travelY = abs(targetY - fromY).coerceAtLeast(4f)
        val rawTargetX = (fromX + aimRatio * travelY * 0.72f + scatterX)
            .coerceIn(CourtSpec.COURT_LEFT_X + 1.1f, CourtSpec.COURT_RIGHT_X - 1.1f)

        return computeTargetShotVelocity(fromX, fromY, fromZ, rawTargetX, targetY, shotType)
    }

    /**
     * Sprite scale factor as ball height increases.
     */
    fun spriteScaleForHeight(z: Float): Float = 1f + min(z, 18f) * 0.085f
}
