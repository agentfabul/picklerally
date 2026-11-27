package com.picklerally.app.game

enum class ScoringMode {
    RALLY,
    SIDE_OUT,
}

enum class MatchMode {
    SINGLES,
    DOUBLES,
}

enum class FaultReason(val calloutText: String) {
    OUT_OF_BOUNDS("Out!"),
    INTO_NET("Net!"),
    DOUBLE_BOUNCE("Double bounce!"),
    KITCHEN_VOLLEY("Kitchen fault!"),
    TWO_BOUNCE_VIOLATION("Two-bounce fault!"),
    SERVE_OUT_OF_BOX("Serve fault!"),
    SERVE_FOOT_FAULT("Foot fault!"),
}

data class ServeAssignment(
    val servingSide: CourtSide,
    val serverIndexInTeam: Int,
    val serverNumberCall: Int,
    val serverBox: ServiceBoxSide,
    val targetCourtSide: CourtSide,
    val targetBox: ServiceBoxSide,
)

data class PointResolution(
    val rallyWinner: CourtSide,
    val faultReason: FaultReason,
    val pointAwardedTo: CourtSide?,
    val sideOutOccurred: Boolean,
    val matchWinner: CourtSide?,
    val calloutText: String,
)

class RulesEngine(
    val scoringMode: ScoringMode = ScoringMode.RALLY,
    val matchMode: MatchMode = MatchMode.SINGLES,
    val pointsToWin: Int = 11,
    val winByMargin: Int = 2,
    initialServer: CourtSide = CourtSide.LOWER,
) {
    var scoreLower: Int = 0
        private set
    var scoreUpper: Int = 0
        private set
    var servingSide: CourtSide = initialServer
        private set
    var serverNumberCall: Int = if (matchMode == MatchMode.DOUBLES) 2 else 1
        private set
    var lowerRightPlayerIndex: Int = 0
        private set
    var upperRightPlayerIndex: Int = 0
        private set
    var rallyShotCount: Int = 0
        private set
    var matchWinner: CourtSide? = null
        private set

    private var isOpeningServiceTurn: Boolean = matchMode == MatchMode.DOUBLES

    val isMatchOver: Boolean
        get() = matchWinner != null

    fun reset(initialServer: CourtSide = CourtSide.LOWER) {
        scoreLower = 0
        scoreUpper = 0
        servingSide = initialServer
        serverNumberCall = if (matchMode == MatchMode.DOUBLES) 2 else 1
        lowerRightPlayerIndex = 0
        upperRightPlayerIndex = 0
        isOpeningServiceTurn = matchMode == MatchMode.DOUBLES
        rallyShotCount = 0
        matchWinner = null
    }

    fun currentServeAssignment(): ServeAssignment {
        val servingScore = if (servingSide == CourtSide.LOWER) scoreLower else scoreUpper
        val isEvenScore = servingScore % 2 == 0

        val serverWorldBox = when (servingSide) {
            CourtSide.LOWER -> if (isEvenScore) ServiceBoxSide.RIGHT else ServiceBoxSide.LEFT
            CourtSide.UPPER -> if (isEvenScore) ServiceBoxSide.LEFT else ServiceBoxSide.RIGHT
        }

        val targetWorldBox =
            if (serverWorldBox == ServiceBoxSide.RIGHT) ServiceBoxSide.LEFT else ServiceBoxSide.RIGHT
        val targetCourtSide =
            if (servingSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER

        var serverIndex = 0
        if (matchMode == MatchMode.DOUBLES) {
            val rightPlayer =
                if (servingSide == CourtSide.LOWER) lowerRightPlayerIndex else upperRightPlayerIndex
            serverIndex = if (isEvenScore) rightPlayer else 1 - rightPlayer
        }

        return ServeAssignment(
            servingSide = servingSide,
            serverIndexInTeam = serverIndex,
            serverNumberCall = serverNumberCall,
            serverBox = serverWorldBox,
            targetCourtSide = targetCourtSide,
            targetBox = targetWorldBox,
        )
    }

    fun beginServe(serverX: Float, serverY: Float): FaultReason? {
        if (isMatchOver) return null
        if (
            !isServePositionValid(
                servingSide,
                serverX,
                serverY,
                currentServeAssignment().serverBox,
            )
        ) {
            return FaultReason.SERVE_FOOT_FAULT
        }
        rallyShotCount = 1
        return null
    }

    fun isServePositionValid(
        side: CourtSide,
        x: Float,
        y: Float,
        expectedBox: ServiceBoxSide,
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

    fun isServeLandingValid(x: Float, y: Float, assignment: ServeAssignment): Boolean {
        val xOk = when (assignment.targetBox) {
            ServiceBoxSide.LEFT -> x in CourtSpec.COURT_LEFT_X..CourtSpec.COURT_CENTER_X
            ServiceBoxSide.RIGHT -> x in CourtSpec.COURT_CENTER_X..CourtSpec.COURT_RIGHT_X
        }
        if (!xOk) return false

        return when (assignment.targetCourtSide) {
            CourtSide.UPPER -> y > CourtSpec.UPPER_NVZ_Y && y <= CourtSpec.COURT_TOP_Y
            CourtSide.LOWER -> y < CourtSpec.LOWER_NVZ_Y && y >= CourtSpec.COURT_BOTTOM_Y
        }
    }

    fun validatePlayerHit(
        hitterSide: CourtSide,
        hitterX: Float,
        hitterY: Float,
        bouncesOnCurrentSide: Int,
    ): FaultReason? {
        val isVolley = bouncesOnCurrentSide == 0

        if (isVolley && (rallyShotCount == 1 || rallyShotCount == 2)) {
            return FaultReason.TWO_BOUNCE_VIOLATION
        }

        if (isVolley && CourtSpec.isInKitchen(hitterX, hitterY, hitterSide)) {
            return FaultReason.KITCHEN_VOLLEY
        }

        rallyShotCount += 1
        return null
    }

    fun validateFirstBounce(x: Float, y: Float, hitterSide: CourtSide? = null): FaultReason? {
        if (rallyShotCount == 1) {
            if (!isServeLandingValid(x, y, currentServeAssignment())) {
                return FaultReason.SERVE_OUT_OF_BOX
            }
            return null
        }

        val xInBounds = x in CourtSpec.COURT_LEFT_X..CourtSpec.COURT_RIGHT_X
        if (!xInBounds) {
            return FaultReason.OUT_OF_BOUNDS
        }

        if (hitterSide == CourtSide.UPPER) {
            return if (y in CourtSpec.COURT_BOTTOM_Y..CourtSpec.NET_Y) null else FaultReason.OUT_OF_BOUNDS
        }
        if (hitterSide == CourtSide.LOWER) {
            return if (y in CourtSpec.NET_Y..CourtSpec.COURT_TOP_Y) null else FaultReason.OUT_OF_BOUNDS
        }

        return if (CourtSpec.isInBounds(x, y)) null else FaultReason.OUT_OF_BOUNDS
    }

    fun resolveFault(faultingSide: CourtSide, reason: FaultReason): PointResolution {
        val rallyWinner =
            if (faultingSide == CourtSide.LOWER) CourtSide.UPPER else CourtSide.LOWER
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
                calloutText = reason.calloutText,
            )
        }

        var pointAwardedTo: CourtSide? = null
        var sideOutOccurred = false

        if (scoringMode == ScoringMode.RALLY) {
            if (rallyWinner == CourtSide.LOWER) scoreLower += 1 else scoreUpper += 1
            pointAwardedTo = rallyWinner

            if (rallyWinner == servingSide) {
                if (matchMode == MatchMode.DOUBLES) swapTeammates(servingSide)
            } else {
                sideOutOccurred = true
                servingSide = rallyWinner
                serverNumberCall = 1
            }
        } else {
            if (rallyWinner == servingSide) {
                if (servingSide == CourtSide.LOWER) scoreLower += 1 else scoreUpper += 1
                pointAwardedTo = servingSide
                if (matchMode == MatchMode.DOUBLES) swapTeammates(servingSide)
            } else {
                if (
                    matchMode == MatchMode.SINGLES ||
                    serverNumberCall == 2 ||
                    isOpeningServiceTurn
                ) {
                    sideOutOccurred = true
                    isOpeningServiceTurn = false
                    servingSide = rallyWinner
                    serverNumberCall = 1
                } else {
                    serverNumberCall = 2
                }
            }
        }

        rallyShotCount = 0
        checkForMatchWinner()

        val callout =
            if (sideOutOccurred && scoringMode == ScoringMode.SIDE_OUT) {
                "${reason.calloutText} Side out!"
            } else {
                reason.calloutText
            }

        return PointResolution(
            rallyWinner = rallyWinner,
            faultReason = reason,
            pointAwardedTo = pointAwardedTo,
            sideOutOccurred = sideOutOccurred,
            matchWinner = matchWinner,
            calloutText = callout,
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
        val lowerWon = scoreLower >= pointsToWin && scoreLower - scoreUpper >= winByMargin
        val upperWon = scoreUpper >= pointsToWin && scoreUpper - scoreLower >= winByMargin
        if (lowerWon) matchWinner = CourtSide.LOWER
        else if (upperWon) matchWinner = CourtSide.UPPER
    }

    fun formattedScoreCall(): String {
        return if (matchMode == MatchMode.SINGLES) {
            if (servingSide == CourtSide.LOWER) "$scoreLower - $scoreUpper"
            else "$scoreUpper - $scoreLower"
        } else {
            if (servingSide == CourtSide.LOWER) "$scoreLower - $scoreUpper - $serverNumberCall"
            else "$scoreUpper - $scoreLower - $serverNumberCall"
        }
    }
}
