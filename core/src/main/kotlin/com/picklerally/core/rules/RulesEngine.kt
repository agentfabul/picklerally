package com.picklerally.core.rules

import com.picklerally.core.court.CourtSide
import com.picklerally.core.court.CourtSpec
import com.picklerally.core.court.ServiceBoxSide

enum class ScoringMode(val label: String) {
    RALLY("Rally to 11"),
    SIDE_OUT("Side-Out to 11")
}

enum class MatchMode {
    SINGLES,
    DOUBLES
}

enum class FaultReason(val callout: String) {
    OUT_OF_BOUNDS("Out!"),
    INTO_NET("Net!"),
    DOUBLE_BOUNCE("Double bounce!"),
    KITCHEN_VOLLEY("Kitchen fault!"),
    TWO_BOUNCE_VIOLATION("Two-bounce fault!"),
    SERVE_OUT_OF_BOX("Serve fault!"),
    SERVE_FOOT_FAULT("Foot fault!")
}

data class ServeAssignment(
    val servingSide: CourtSide,
    val serverIndexInTeam: Int, // 0 or 1 for doubles; always 0 for singles
    val serverNumberCall: Int,  // 1 or 2 in doubles; 1 in singles
    val serverBox: ServiceBoxSide,
    val targetCourtSide: CourtSide,
    val targetBox: ServiceBoxSide
)

data class PointResolution(
    val rallyWinner: CourtSide,
    val faultReason: FaultReason,
    val pointAwardedTo: CourtSide?,
    val sideOutOccurred: Boolean,
    val matchWinner: CourtSide?,
    val calloutText: String
)

data class RulesSnapshot(
    val scoreLower: Int,
    val scoreUpper: Int,
    val servingSide: CourtSide,
    val serverNumberCall: Int,
    val lowerRightPlayerIndex: Int,
    val upperRightPlayerIndex: Int,
    val rallyShotCount: Int, // 1 = serve, 2 = return, 3 = third shot, 4+ = open rally
    val isMatchOver: Boolean,
    val matchWinner: CourtSide?
)

/**
 * Pure Kotlin pickleball rules and scoring engine in core/.
 * Enforces:
 * - Underhand serve from behind baseline into diagonal service box (NVZ line = fault)
 * - Two-bounce rule (serve return & third shot must bounce before hitting)
 * - Kitchen (non-volley zone) rule (no volleying while touching kitchen or NVZ line)
 * - Out of bounds, into net, double bounce, foot fault
 * - Rally scoring to 11 (win by 2) and Side-Out scoring to 11 (win by 2)
 * - Singles & Doubles serving rotation and side switching
 */
class RulesEngine(
    val scoringMode: ScoringMode = ScoringMode.RALLY,
    val matchMode: MatchMode = MatchMode.SINGLES,
    val pointsToWin: Int = 11,
    val winByMargin: Int = 2,
    initialServer: CourtSide = CourtSide.LOWER
) {
    var scoreLower: Int = 0
        private set
    var scoreUpper: Int = 0
        private set

    var servingSide: CourtSide = initialServer
        private set

    // In doubles, first serving team begins at server #2 ("0-0-2" start)
    var serverNumberCall: Int = if (matchMode == MatchMode.DOUBLES) 2 else 1
        private set

    // Track which teammate (0 or 1) occupies each team's RIGHT (even) court side.
    // For LOWER facing +Y: right side is X in [14..24].
    // For UPPER facing -Y: right side is X in [4..14].
    var lowerRightPlayerIndex: Int = 0
        private set
    var upperRightPlayerIndex: Int = 0
        private set

    // Tracks who started the match in the right court so we know which server is #1 vs #2
    private var lowerFirstServerIndex: Int = 0
    private var upperFirstServerIndex: Int = 0
    private var isOpeningServiceTurn: Boolean = (matchMode == MatchMode.DOUBLES)

    // Rally-level shot counter: 0 = waiting serve, 1 = serve in flight, 2 = return in flight, 3 = third shot
    var rallyShotCount: Int = 0
        private set

    var matchWinner: CourtSide? = null
        private set

    val isMatchOver: Boolean
        get() = matchWinner != null

    fun reset(initialServer: CourtSide = CourtSide.LOWER) {
        scoreLower = 0
        scoreUpper = 0
        servingSide = initialServer
        serverNumberCall = if (matchMode == MatchMode.DOUBLES) 2 else 1
        lowerRightPlayerIndex = 0
        upperRightPlayerIndex = 0
        lowerFirstServerIndex = 0
        upperFirstServerIndex = 0
        isOpeningServiceTurn = (matchMode == MatchMode.DOUBLES)
        rallyShotCount = 0
        matchWinner = null
    }

    fun snapshot(): RulesSnapshot = RulesSnapshot(
        scoreLower = scoreLower,
        scoreUpper = scoreUpper,
        servingSide = servingSide,
        serverNumberCall = serverNumberCall,
        lowerRightPlayerIndex = lowerRightPlayerIndex,
        upperRightPlayerIndex = upperRightPlayerIndex,
        rallyShotCount = rallyShotCount,
        isMatchOver = isMatchOver,
        matchWinner = matchWinner
    )

    fun currentServeAssignment(): ServeAssignment {
        val servingScore = if (servingSide == CourtSide.LOWER) scoreLower else scoreUpper
        val isEvenScore = servingScore % 2 == 0

        // From each player's own perspective facing the net:
        // LOWER faces +Y -> right is RIGHT (x >= 14), left is LEFT (x <= 14)
        // UPPER faces -Y -> right is LEFT in world X (x <= 14), left is RIGHT in world X (x >= 14)
        val serverWorldBox = when (servingSide) {
            CourtSide.LOWER -> if (isEvenScore) ServiceBoxSide.RIGHT else ServiceBoxSide.LEFT
            CourtSide.UPPER -> if (isEvenScore) ServiceBoxSide.LEFT else ServiceBoxSide.RIGHT
        }

        // Diagonal opposite box across the net
        val targetWorldBox = when (serverWorldBox) {
            ServiceBoxSide.RIGHT -> ServiceBoxSide.LEFT
            ServiceBoxSide.LEFT -> ServiceBoxSide.RIGHT
        }
        val targetCourtSide = if (servingSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER

        val serverIndex = if (matchMode == MatchMode.SINGLES) {
            0
        } else {
            val rightPlayer = if (servingSide == CourtSide.LOWER) lowerRightPlayerIndex else upperRightPlayerIndex
            if (isEvenScore) rightPlayer else 1 - rightPlayer
        }

        return ServeAssignment(
            servingSide = servingSide,
            serverIndexInTeam = serverIndex,
            serverNumberCall = serverNumberCall,
            serverBox = serverWorldBox,
            targetCourtSide = targetCourtSide,
            targetBox = targetWorldBox
        )
    }

    fun beginServe(serverX: Float, serverY: Float): FaultReason? {
        if (isMatchOver) return null
        if (!isServePositionValid(servingSide, serverX, serverY, currentServeAssignment().serverBox)) {
            return FaultReason.SERVE_FOOT_FAULT
        }
        rallyShotCount = 1
        return null
    }

    fun isServePositionValid(
        side: CourtSide,
        x: Float,
        y: Float,
        expectedBox: ServiceBoxSide
    ): Boolean {
        val behindBaseline = when (side) {
            CourtSide.LOWER -> y <= CourtSpec.COURT_BOTTOM_Y
            CourtSide.UPPER -> y >= CourtSpec.COURT_TOP_Y
        }
        if (!behindBaseline) return false

        return when (expectedBox) {
            ServiceBoxSide.LEFT -> x in (CourtSpec.COURT_LEFT_X - 0.5f)..CourtSpec.COURT_CENTER_X
            ServiceBoxSide.RIGHT -> x in CourtSpec.COURT_CENTER_X..(CourtSpec.COURT_RIGHT_X + 0.5f)
        }
    }

    /**
     * Serve must land within the opponent's diagonal service box and MUST clear the NVZ line.
     */
    fun isServeLandingValid(x: Float, y: Float, assignment: ServeAssignment): Boolean {
        val xOk = when (assignment.targetBox) {
            ServiceBoxSide.LEFT -> x in CourtSpec.COURT_LEFT_X..CourtSpec.COURT_CENTER_X
            ServiceBoxSide.RIGHT -> x in CourtSpec.COURT_CENTER_X..CourtSpec.COURT_RIGHT_X
        }
        if (!xOk) return false

        // NVZ line is a fault on a serve (strictly past NVZ line, inclusive of baseline)
        return when (assignment.targetCourtSide) {
            CourtSide.UPPER -> y > CourtSpec.UPPER_NVZ_Y && y <= CourtSpec.COURT_TOP_Y
            CourtSide.LOWER -> y < CourtSpec.LOWER_NVZ_Y && y >= CourtSpec.COURT_BOTTOM_Y
        }
    }

    /**
     * Validate whether a swing attempt is legal under two-bounce and kitchen rules.
     * Returns a FaultReason if illegal, or null if the hit is legal (advancing rallyShotCount).
     */
    fun validatePlayerHit(
        hitterSide: CourtSide,
        hitterX: Float,
        hitterY: Float,
        bouncesOnCurrentSide: Int
    ): FaultReason? {
        val isVolley = bouncesOnCurrentSide == 0

        // Two-bounce rule: shot 1 (serve) and shot 2 (return of serve) must bounce before being struck
        if (isVolley && (rallyShotCount == 1 || rallyShotCount == 2)) {
            return FaultReason.TWO_BOUNCE_VIOLATION
        }

        // Kitchen (Non-Volley Zone) rule: cannot volley while touching kitchen or NVZ line
        if (isVolley && CourtSpec.isInKitchen(hitterX, hitterY, hitterSide)) {
            return FaultReason.KITCHEN_VOLLEY
        }

        rallyShotCount += 1
        return null
    }

    /**
     * Validate first ground bounce on the receiving side.
     */
    fun validateFirstBounce(x: Float, y: Float, hitterSide: CourtSide? = null): FaultReason? {
        if (rallyShotCount == 1) {
            val assignment = currentServeAssignment()
            if (!isServeLandingValid(x, y, assignment)) {
                return FaultReason.SERVE_OUT_OF_BOX
            }
            return null
        }

        val xInBounds = x in CourtSpec.COURT_LEFT_X..CourtSpec.COURT_RIGHT_X
        if (!xInBounds) {
            return FaultReason.OUT_OF_BOUNDS
        }

        return when (hitterSide) {
            CourtSide.UPPER -> if (y in CourtSpec.COURT_BOTTOM_Y..CourtSpec.NET_Y) null else FaultReason.OUT_OF_BOUNDS
            CourtSide.LOWER -> if (y in CourtSpec.NET_Y..CourtSpec.COURT_TOP_Y) null else FaultReason.OUT_OF_BOUNDS
            null -> if (CourtSpec.isInBounds(x, y)) null else FaultReason.OUT_OF_BOUNDS
        }
    }

    /**
     * Resolve a fault committed by `faultingSide`, updating scores, serving rotation,
     * and side switches according to ScoringMode and MatchMode.
     */
    fun resolveFault(faultingSide: CourtSide, reason: FaultReason): PointResolution {
        val rallyWinner = if (faultingSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
        return applyRallyOutcome(rallyWinner, reason)
    }

    fun applyRallyOutcome(rallyWinner: CourtSide, reason: FaultReason): PointResolution {
        if (isMatchOver) {
            return PointResolution(
                rallyWinner = rallyWinner,
                faultReason = reason,
                pointAwardedTo = null,
                sideOutOccurred = false,
                matchWinner = matchWinner,
                calloutText = reason.callout
            )
        }

        var pointAwardedTo: CourtSide? = null
        var sideOutOccurred = false

        when (scoringMode) {
            ScoringMode.RALLY -> {
                if (rallyWinner == CourtSide.LOWER) scoreLower += 1 else scoreUpper += 1
                pointAwardedTo = rallyWinner

                if (rallyWinner == servingSide) {
                    if (matchMode == MatchMode.DOUBLES) swapTeammates(servingSide)
                } else {
                    sideOutOccurred = true
                    servingSide = rallyWinner
                    serverNumberCall = 1
                }
            }

            ScoringMode.SIDE_OUT -> {
                if (rallyWinner == servingSide) {
                    if (servingSide == CourtSide.LOWER) scoreLower += 1 else scoreUpper += 1
                    pointAwardedTo = servingSide
                    if (matchMode == MatchMode.DOUBLES) {
                        swapTeammates(servingSide)
                    }
                } else {
                    // Receiving team won the rally
                    if (matchMode == MatchMode.SINGLES || serverNumberCall == 2 || isOpeningServiceTurn) {
                        sideOutOccurred = true
                        isOpeningServiceTurn = false
                        servingSide = rallyWinner
                        serverNumberCall = 1
                    } else {
                        serverNumberCall = 2
                    }
                }
            }
        }

        rallyShotCount = 0
        checkForMatchWinner()

        val callout = if (sideOutOccurred && scoringMode == ScoringMode.SIDE_OUT) {
            "${reason.callout} Side out!"
        } else {
            reason.callout
        }

        return PointResolution(
            rallyWinner = rallyWinner,
            faultReason = reason,
            pointAwardedTo = pointAwardedTo,
            sideOutOccurred = sideOutOccurred,
            matchWinner = matchWinner,
            calloutText = callout
        )
    }

    private fun swapTeammates(side: CourtSide) {
        if (side == CourtSide.LOWER) {
            lowerRightPlayerIndex = 1 - lowerRightPlayerIndex
        } else {
            upperRightPlayerIndex = 1 - upperRightPlayerIndex
        }
    }

    private fun checkForMatchWinner() {
        val lowerWon = scoreLower >= pointsToWin && (scoreLower - scoreUpper) >= winByMargin
        val upperWon = scoreUpper >= pointsToWin && (scoreUpper - scoreLower) >= winByMargin
        matchWinner = when {
            lowerWon -> CourtSide.LOWER
            upperWon -> CourtSide.UPPER
            else -> null
        }
    }

    fun formattedScoreCall(): String = when (matchMode) {
        MatchMode.SINGLES -> {
            if (servingSide == CourtSide.LOWER) "$scoreLower - $scoreUpper" else "$scoreUpper - $scoreLower"
        }
        MatchMode.DOUBLES -> {
            if (servingSide == CourtSide.LOWER) {
                "$scoreLower - $scoreUpper - $serverNumberCall"
            } else {
                "$scoreUpper - $scoreLower - $serverNumberCall"
            }
        }
    }
}
