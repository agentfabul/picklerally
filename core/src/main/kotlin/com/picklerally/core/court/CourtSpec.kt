package com.picklerally.core.court

enum class CourtSide {
    LOWER,
    UPPER
}

enum class ServiceBoxSide {
    LEFT,
    RIGHT
}

enum class CourtZone {
    LOWER_KITCHEN,
    UPPER_KITCHEN,
    LOWER_LEFT_SERVICE,
    LOWER_RIGHT_SERVICE,
    UPPER_LEFT_SERVICE,
    UPPER_RIGHT_SERVICE,
    NET_LINE,
    OUT_OF_BOUNDS
}

/**
 * Official USA Pickleball dimensions mapped onto a portrait world coordinate space.
 * Units are in feet (1 world unit = 1 foot).
 *
 * World bounds: 28 ft wide x 56 ft tall (1:2 portrait ratio for FitViewport).
 * Playing court: 20 ft wide x 44 ft tall, centered inside the apron.
 */
object CourtSpec {
    const val WORLD_WIDTH_FT = 28f
    const val WORLD_HEIGHT_FT = 56f

    const val COURT_WIDTH_FT = 20f
    const val COURT_LENGTH_FT = 44f
    const val KITCHEN_DEPTH_FT = 7f
    const val SERVICE_DEPTH_FT = 15f
    const val LINE_WIDTH_FT = 2f / 12f // Official 2-inch court lines

    const val NET_HEIGHT_SIDELINE_FT = 3.0f      // 36 inches at sidelines
    const val NET_HEIGHT_CENTER_FT = 34f / 12f   // 34 inches at center strap
    const val NET_POST_OFFSET_FT = 1.0f          // Posts sit 1 ft outside sidelines

    const val COURT_LEFT_X = (WORLD_WIDTH_FT - COURT_WIDTH_FT) / 2f   // 4f
    const val COURT_RIGHT_X = COURT_LEFT_X + COURT_WIDTH_FT           // 24f
    const val COURT_CENTER_X = WORLD_WIDTH_FT / 2f                    // 14f

    const val COURT_BOTTOM_Y = (WORLD_HEIGHT_FT - COURT_LENGTH_FT) / 2f // 6f
    const val COURT_TOP_Y = COURT_BOTTOM_Y + COURT_LENGTH_FT            // 50f
    const val NET_Y = WORLD_HEIGHT_FT / 2f                              // 28f

    const val LOWER_NVZ_Y = NET_Y - KITCHEN_DEPTH_FT                    // 21f
    const val UPPER_NVZ_Y = NET_Y + KITCHEN_DEPTH_FT                    // 35f

    const val FIXED_TIMESTEP_SEC = 1f / 60f

    fun isInBounds(x: Float, y: Float): Boolean =
        x in COURT_LEFT_X..COURT_RIGHT_X && y in COURT_BOTTOM_Y..COURT_TOP_Y

    fun isInKitchen(x: Float, y: Float, side: CourtSide? = null): Boolean {
        if (x !in COURT_LEFT_X..COURT_RIGHT_X) return false
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
        boxSide: ServiceBoxSide
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
        if (kotlin.math.abs(y - NET_Y) <= LINE_WIDTH_FT * 0.5f) return CourtZone.NET_LINE

        return when {
            y < LOWER_NVZ_Y -> if (x <= COURT_CENTER_X) CourtZone.LOWER_LEFT_SERVICE else CourtZone.LOWER_RIGHT_SERVICE
            y < NET_Y -> CourtZone.LOWER_KITCHEN
            y <= UPPER_NVZ_Y -> CourtZone.UPPER_KITCHEN
            else -> if (x <= COURT_CENTER_X) CourtZone.UPPER_LEFT_SERVICE else CourtZone.UPPER_RIGHT_SERVICE
        }
    }

    fun netHeightAt(x: Float): Float {
        val normalized = (kotlin.math.abs(x - COURT_CENTER_X) / (COURT_WIDTH_FT / 2f)).coerceIn(0f, 1f)
        return NET_HEIGHT_CENTER_FT + (NET_HEIGHT_SIDELINE_FT - NET_HEIGHT_CENTER_FT) * normalized
    }
}
