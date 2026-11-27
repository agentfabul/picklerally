package com.picklerally.app.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class CourtSide {
    LOWER,
    UPPER,
}

enum class ServiceBoxSide {
    LEFT,
    RIGHT,
}

enum class CourtZone {
    LOWER_KITCHEN,
    UPPER_KITCHEN,
    LOWER_LEFT_SERVICE,
    LOWER_RIGHT_SERVICE,
    UPPER_LEFT_SERVICE,
    UPPER_RIGHT_SERVICE,
    NET_LINE,
    OUT_OF_BOUNDS,
}

object CourtSpec {
    const val WORLD_WIDTH_FT = 28f
    const val WORLD_HEIGHT_FT = 56f

    const val COURT_WIDTH_FT = 20f
    const val COURT_LENGTH_FT = 44f
    const val KITCHEN_DEPTH_FT = 7f
    const val SERVICE_DEPTH_FT = 15f
    const val LINE_WIDTH_FT = 2f / 12f

    const val NET_HEIGHT_SIDELINE_FT = 3.0f
    const val NET_HEIGHT_CENTER_FT = 34f / 12f
    const val NET_POST_OFFSET_FT = 1.0f

    const val COURT_LEFT_X = 4f
    const val COURT_RIGHT_X = 24f
    const val COURT_CENTER_X = 14f

    const val COURT_BOTTOM_Y = 6f
    const val COURT_TOP_Y = 50f
    const val NET_Y = 28f

    const val LOWER_NVZ_Y = 21f
    const val UPPER_NVZ_Y = 35f

    const val FIXED_TIMESTEP_SEC = 1f / 60f

    fun isInBounds(x: Float, y: Float): Boolean =
        x in COURT_LEFT_X..COURT_RIGHT_X && y in COURT_BOTTOM_Y..COURT_TOP_Y

    fun isInKitchen(x: Float, y: Float, side: CourtSide? = null): Boolean {
        if (x < COURT_LEFT_X || x > COURT_RIGHT_X) return false
        return when (side) {
            CourtSide.LOWER -> y in LOWER_NVZ_Y..NET_Y
            CourtSide.UPPER -> y in NET_Y..UPPER_NVZ_Y
            null -> y in LOWER_NVZ_Y..UPPER_NVZ_Y
        }
    }

    fun isInServiceBox(
        x: Float,
        y: Float,
        courtSide: CourtSide,
        boxSide: ServiceBoxSide,
    ): Boolean {
        val xMatch = when (boxSide) {
            ServiceBoxSide.LEFT -> x in COURT_LEFT_X..COURT_CENTER_X
            ServiceBoxSide.RIGHT -> x in COURT_CENTER_X..COURT_RIGHT_X
        }
        if (!xMatch) return false
        return when (courtSide) {
            CourtSide.LOWER -> y in COURT_BOTTOM_Y..LOWER_NVZ_Y
            CourtSide.UPPER -> y in UPPER_NVZ_Y..COURT_TOP_Y
        }
    }

    fun classifyPoint(x: Float, y: Float): CourtZone {
        if (!isInBounds(x, y)) return CourtZone.OUT_OF_BOUNDS
        if (abs(y - NET_Y) <= LINE_WIDTH_FT * 0.5f) return CourtZone.NET_LINE

        if (y < LOWER_NVZ_Y) {
            return if (x <= COURT_CENTER_X) CourtZone.LOWER_LEFT_SERVICE else CourtZone.LOWER_RIGHT_SERVICE
        }
        if (y < NET_Y) return CourtZone.LOWER_KITCHEN
        if (y <= UPPER_NVZ_Y) return CourtZone.UPPER_KITCHEN
        return if (x <= COURT_CENTER_X) CourtZone.UPPER_LEFT_SERVICE else CourtZone.UPPER_RIGHT_SERVICE
    }

    fun netHeightAt(x: Float): Float {
        val normalized = min(
            1f,
            max(0f, abs(x - COURT_CENTER_X) / (COURT_WIDTH_FT / 2f)),
        )
        return NET_HEIGHT_CENTER_FT +
            (NET_HEIGHT_SIDELINE_FT - NET_HEIGHT_CENTER_FT) * normalized
    }
}
