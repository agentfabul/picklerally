import { CourtSpec, type CourtSide, type ServiceBoxSide, type SpecTestResult } from './courtSpec';

export type ScoringMode = 'RALLY' | 'SIDE_OUT';
export type MatchMode = 'SINGLES' | 'DOUBLES';

export type FaultReason =
  | 'OUT_OF_BOUNDS'
  | 'INTO_NET'
  | 'DOUBLE_BOUNCE'
  | 'KITCHEN_VOLLEY'
  | 'TWO_BOUNCE_VIOLATION'
  | 'SERVE_OUT_OF_BOX'
  | 'SERVE_FOOT_FAULT';

export const FAULT_CALLOUTS: Record<FaultReason, string> = {
  OUT_OF_BOUNDS: 'Out!',
  INTO_NET: 'Net!',
  DOUBLE_BOUNCE: 'Double bounce!',
  KITCHEN_VOLLEY: 'Kitchen fault!',
  TWO_BOUNCE_VIOLATION: 'Two-bounce fault!',
  SERVE_OUT_OF_BOX: 'Serve fault!',
  SERVE_FOOT_FAULT: 'Foot fault!',
};

export interface ServeAssignment {
  servingSide: CourtSide;
  serverIndexInTeam: number;
  serverNumberCall: number;
  serverBox: ServiceBoxSide;
  targetCourtSide: CourtSide;
  targetBox: ServiceBoxSide;
}

export interface PointResolution {
  rallyWinner: CourtSide;
  faultReason: FaultReason;
  pointAwardedTo: CourtSide | null;
  sideOutOccurred: boolean;
  matchWinner: CourtSide | null;
  calloutText: string;
}

export class RulesEngine {
  readonly scoringMode: ScoringMode;
  readonly matchMode: MatchMode;
  readonly pointsToWin: number;
  readonly winByMargin: number;

  scoreLower = 0;
  scoreUpper = 0;
  servingSide: CourtSide;
  serverNumberCall: number;
  lowerRightPlayerIndex = 0;
  upperRightPlayerIndex = 0;
  rallyShotCount = 0;
  matchWinner: CourtSide | null = null;

  private isOpeningServiceTurn: boolean;

  constructor(
    scoringMode: ScoringMode = 'RALLY',
    matchMode: MatchMode = 'SINGLES',
    pointsToWin = 11,
    winByMargin = 2,
    initialServer: CourtSide = 'LOWER',
  ) {
    this.scoringMode = scoringMode;
    this.matchMode = matchMode;
    this.pointsToWin = pointsToWin;
    this.winByMargin = winByMargin;
    this.servingSide = initialServer;
    this.serverNumberCall = matchMode === 'DOUBLES' ? 2 : 1;
    this.isOpeningServiceTurn = matchMode === 'DOUBLES';
  }

  get isMatchOver(): boolean {
    return this.matchWinner !== null;
  }

  reset(initialServer: CourtSide = 'LOWER') {
    this.scoreLower = 0;
    this.scoreUpper = 0;
    this.servingSide = initialServer;
    this.serverNumberCall = this.matchMode === 'DOUBLES' ? 2 : 1;
    this.lowerRightPlayerIndex = 0;
    this.upperRightPlayerIndex = 0;
    this.isOpeningServiceTurn = this.matchMode === 'DOUBLES';
    this.rallyShotCount = 0;
    this.matchWinner = null;
  }

  currentServeAssignment(): ServeAssignment {
    const servingScore = this.servingSide === 'LOWER' ? this.scoreLower : this.scoreUpper;
    const isEvenScore = servingScore % 2 === 0;

    const serverWorldBox: ServiceBoxSide =
      this.servingSide === 'LOWER'
        ? isEvenScore
          ? 'RIGHT'
          : 'LEFT'
        : isEvenScore
          ? 'LEFT'
          : 'RIGHT';

    const targetWorldBox: ServiceBoxSide = serverWorldBox === 'RIGHT' ? 'LEFT' : 'RIGHT';
    const targetCourtSide: CourtSide = this.servingSide === 'LOWER' ? 'UPPER' : 'LOWER';

    let serverIndex = 0;
    if (this.matchMode === 'DOUBLES') {
      const rightPlayer =
        this.servingSide === 'LOWER' ? this.lowerRightPlayerIndex : this.upperRightPlayerIndex;
      serverIndex = isEvenScore ? rightPlayer : 1 - rightPlayer;
    }

    return {
      servingSide: this.servingSide,
      serverIndexInTeam: serverIndex,
      serverNumberCall: this.serverNumberCall,
      serverBox: serverWorldBox,
      targetCourtSide,
      targetBox: targetWorldBox,
    };
  }

  beginServe(serverX: number, serverY: number): FaultReason | null {
    if (this.isMatchOver) return null;
    if (
      !this.isServePositionValid(
        this.servingSide,
        serverX,
        serverY,
        this.currentServeAssignment().serverBox,
      )
    ) {
      return 'SERVE_FOOT_FAULT';
    }
    this.rallyShotCount = 1;
    return null;
  }

  isServePositionValid(
    side: CourtSide,
    x: number,
    y: number,
    expectedBox: ServiceBoxSide,
  ): boolean {
    const behindBaseline =
      side === 'LOWER' ? y <= CourtSpec.COURT_BOTTOM_Y : y >= CourtSpec.COURT_TOP_Y;
    if (!behindBaseline) return false;

    return expectedBox === 'LEFT'
      ? x >= CourtSpec.COURT_LEFT_X - 0.5 && x <= CourtSpec.COURT_CENTER_X
      : x >= CourtSpec.COURT_CENTER_X && x <= CourtSpec.COURT_RIGHT_X + 0.5;
  }

  isServeLandingValid(x: number, y: number, assignment: ServeAssignment): boolean {
    const xOk =
      assignment.targetBox === 'LEFT'
        ? x >= CourtSpec.COURT_LEFT_X && x <= CourtSpec.COURT_CENTER_X
        : x >= CourtSpec.COURT_CENTER_X && x <= CourtSpec.COURT_RIGHT_X;
    if (!xOk) return false;

    return assignment.targetCourtSide === 'UPPER'
      ? y > CourtSpec.UPPER_NVZ_Y && y <= CourtSpec.COURT_TOP_Y
      : y < CourtSpec.LOWER_NVZ_Y && y >= CourtSpec.COURT_BOTTOM_Y;
  }

  validatePlayerHit(
    hitterSide: CourtSide,
    hitterX: number,
    hitterY: number,
    bouncesOnCurrentSide: number,
  ): FaultReason | null {
    const isVolley = bouncesOnCurrentSide === 0;

    if (isVolley && (this.rallyShotCount === 1 || this.rallyShotCount === 2)) {
      return 'TWO_BOUNCE_VIOLATION';
    }

    if (isVolley && CourtSpec.isInKitchen(hitterX, hitterY, hitterSide)) {
      return 'KITCHEN_VOLLEY';
    }

    this.rallyShotCount += 1;
    return null;
  }

  validateFirstBounce(x: number, y: number, hitterSide?: CourtSide | null): FaultReason | null {
    if (this.rallyShotCount === 1) {
      if (!this.isServeLandingValid(x, y, this.currentServeAssignment())) {
        return 'SERVE_OUT_OF_BOX';
      }
      return null;
    }

    const xInBounds = x >= CourtSpec.COURT_LEFT_X && x <= CourtSpec.COURT_RIGHT_X;
    if (!xInBounds) {
      return 'OUT_OF_BOUNDS';
    }

    if (hitterSide === 'UPPER') {
      // Shot from top half (bot) must land within bottom half bounds (lines count as in)
      return y >= CourtSpec.COURT_BOTTOM_Y && y <= CourtSpec.NET_Y ? null : 'OUT_OF_BOUNDS';
    }
    if (hitterSide === 'LOWER') {
      // Shot from bottom half (player) must land within top half bounds (lines count as in)
      return y >= CourtSpec.NET_Y && y <= CourtSpec.COURT_TOP_Y ? null : 'OUT_OF_BOUNDS';
    }

    if (!CourtSpec.isInBounds(x, y)) {
      return 'OUT_OF_BOUNDS';
    }
    return null;
  }

  resolveFault(faultingSide: CourtSide, reason: FaultReason): PointResolution {
    const rallyWinner: CourtSide = faultingSide === 'LOWER' ? 'UPPER' : 'LOWER';
    return this.applyRallyOutcome(rallyWinner, reason);
  }

  applyRallyOutcome(rallyWinner: CourtSide, reason: FaultReason): PointResolution {
    if (this.isMatchOver) {
      return {
        rallyWinner,
        faultReason: reason,
        pointAwardedTo: null,
        sideOutOccurred: false,
        matchWinner: this.matchWinner,
        calloutText: FAULT_CALLOUTS[reason],
      };
    }

    let pointAwardedTo: CourtSide | null = null;
    let sideOutOccurred = false;

    if (this.scoringMode === 'RALLY') {
      if (rallyWinner === 'LOWER') this.scoreLower += 1;
      else this.scoreUpper += 1;
      pointAwardedTo = rallyWinner;

      if (rallyWinner === this.servingSide) {
        if (this.matchMode === 'DOUBLES') this.swapTeammates(this.servingSide);
      } else {
        sideOutOccurred = true;
        this.servingSide = rallyWinner;
        this.serverNumberCall = 1;
      }
    } else {
      if (rallyWinner === this.servingSide) {
        if (this.servingSide === 'LOWER') this.scoreLower += 1;
        else this.scoreUpper += 1;
        pointAwardedTo = this.servingSide;
        if (this.matchMode === 'DOUBLES') this.swapTeammates(this.servingSide);
      } else {
        if (
          this.matchMode === 'SINGLES' ||
          this.serverNumberCall === 2 ||
          this.isOpeningServiceTurn
        ) {
          sideOutOccurred = true;
          this.isOpeningServiceTurn = false;
          this.servingSide = rallyWinner;
          this.serverNumberCall = 1;
        } else {
          this.serverNumberCall = 2;
        }
      }
    }

    this.rallyShotCount = 0;
    this.checkForMatchWinner();

    const callout =
      sideOutOccurred && this.scoringMode === 'SIDE_OUT'
        ? `${FAULT_CALLOUTS[reason]} Side out!`
        : FAULT_CALLOUTS[reason];

    return {
      rallyWinner,
      faultReason: reason,
      pointAwardedTo,
      sideOutOccurred,
      matchWinner: this.matchWinner,
      calloutText: callout,
    };
  }

  private swapTeammates(side: CourtSide) {
    if (side === 'LOWER') this.lowerRightPlayerIndex = 1 - this.lowerRightPlayerIndex;
    else this.upperRightPlayerIndex = 1 - this.upperRightPlayerIndex;
  }

  private checkForMatchWinner() {
    const lowerWon =
      this.scoreLower >= this.pointsToWin && this.scoreLower - this.scoreUpper >= this.winByMargin;
    const upperWon =
      this.scoreUpper >= this.pointsToWin && this.scoreUpper - this.scoreLower >= this.winByMargin;
    if (lowerWon) this.matchWinner = 'LOWER';
    else if (upperWon) this.matchWinner = 'UPPER';
  }

  formattedScoreCall(): string {
    if (this.matchMode === 'SINGLES') {
      return this.servingSide === 'LOWER'
        ? `${this.scoreLower} - ${this.scoreUpper}`
        : `${this.scoreUpper} - ${this.scoreLower}`;
    }
    return this.servingSide === 'LOWER'
      ? `${this.scoreLower} - ${this.scoreUpper} - ${this.serverNumberCall}`
      : `${this.scoreUpper} - ${this.scoreLower} - ${this.serverNumberCall}`;
  }
}

export function runRulesEngineTests(): SpecTestResult[] {
  const results: SpecTestResult[] = [];

  // 1. Serve baseline & diagonal service box
  const r1 = new RulesEngine('RALLY', 'SINGLES');
  const a1 = r1.currentServeAssignment();
  const serveOk =
    a1.servingSide === 'LOWER' &&
    a1.serverBox === 'RIGHT' &&
    a1.targetBox === 'LEFT' &&
    r1.beginServe(18, CourtSpec.COURT_BOTTOM_Y + 0.5) === 'SERVE_FOOT_FAULT' &&
    r1.beginServe(18, CourtSpec.COURT_BOTTOM_Y - 0.5) === null &&
    r1.isServeLandingValid(9, 40, a1) &&
    !r1.isServeLandingValid(9, CourtSpec.UPPER_NVZ_Y, a1) &&
    !r1.isServeLandingValid(19, 40, a1);
  results.push({
    id: 'rules-serve-box',
    name: 'Serve baseline foot fault & diagonal box (NVZ line excluded)',
    passed: serveOk,
    detail: 'Even score -> Right-to-Left diagonal · NVZ line = short serve fault',
  });

  // 2. Two-bounce rule
  const r2 = new RulesEngine('RALLY', 'SINGLES');
  r2.beginServe(18, CourtSpec.COURT_BOTTOM_Y - 1);
  const twoBounceOk =
    r2.validatePlayerHit('UPPER', 9, 40, 0) === 'TWO_BOUNCE_VIOLATION' &&
    r2.validatePlayerHit('UPPER', 9, 40, 1) === null &&
    r2.validatePlayerHit('LOWER', 14, 16, 0) === 'TWO_BOUNCE_VIOLATION' &&
    r2.validatePlayerHit('LOWER', 14, 16, 1) === null &&
    r2.validatePlayerHit('UPPER', 14, 36.5, 0) === null;
  results.push({
    id: 'rules-two-bounce',
    name: 'Two-bounce rule (serve return & third shot must bounce)',
    passed: twoBounceOk,
    detail: 'Shot 2 & Shot 3 volley blocked · Shot 4+ open volley allowed',
  });

  // 3. Kitchen volley rule
  const r3 = new RulesEngine('RALLY', 'SINGLES');
  r3.beginServe(18, CourtSpec.COURT_BOTTOM_Y - 1);
  r3.validatePlayerHit('UPPER', 9, 40, 1);
  r3.validatePlayerHit('LOWER', 14, 16, 1);
  const kitchenOk =
    r3.validatePlayerHit('LOWER', 14, 24, 0) === 'KITCHEN_VOLLEY' &&
    r3.validatePlayerHit('LOWER', 14, CourtSpec.LOWER_NVZ_Y, 0) === 'KITCHEN_VOLLEY' &&
    r3.validatePlayerHit('LOWER', 14, 24, 1) === null;
  results.push({
    id: 'rules-kitchen-nvz',
    name: 'Kitchen (NVZ) volley prohibition & ground-stroke exception',
    passed: kitchenOk,
    detail: 'Volley in kitchen or on NVZ line = fault · Bounced ball in kitchen = legal',
  });

  // 4. Rally scoring to 11, win by 2
  const r4 = new RulesEngine('RALLY', 'SINGLES', 11, 2);
  for (let i = 0; i < 10; i++) {
    r4.resolveFault('UPPER', 'OUT_OF_BOUNDS');
    r4.resolveFault('LOWER', 'OUT_OF_BOUNDS');
  }
  const at11 = r4.resolveFault('UPPER', 'INTO_NET');
  const at12 = r4.resolveFault('UPPER', 'DOUBLE_BOUNCE');
  const rallyScoreOk =
    r4.scoreLower === 12 &&
    r4.scoreUpper === 10 &&
    at11.matchWinner === null &&
    at12.matchWinner === 'LOWER' &&
    r4.isMatchOver;
  results.push({
    id: 'rules-rally-11',
    name: 'Rally scoring to 11 with win-by-2 deuce enforcement',
    passed: rallyScoreOk,
    detail: '11-10 continues · 12-10 wins match',
  });

  // 5. Singles Side-Out scoring
  const r5 = new RulesEngine('SIDE_OUT', 'SINGLES');
  const p1 = r5.resolveFault('UPPER', 'OUT_OF_BOUNDS');
  const p2 = r5.resolveFault('LOWER', 'INTO_NET');
  const sideOutOk =
    p1.pointAwardedTo === 'LOWER' &&
    !p1.sideOutOccurred &&
    r5.currentServeAssignment().serverBox === 'LEFT' &&
    p2.pointAwardedTo === null &&
    p2.sideOutOccurred &&
    r5.servingSide === 'UPPER' &&
    r5.scoreLower === 1 &&
    r5.scoreUpper === 0;
  results.push({
    id: 'rules-sideout-singles',
    name: 'Singles Side-Out scoring & odd/even side switching',
    passed: sideOutOk,
    detail: 'Only server scores · Receiver win triggers side-out without point',
  });

  // 6. Doubles 0-0-2 rotation & teammate side swap
  const r6 = new RulesEngine('SIDE_OUT', 'DOUBLES');
  const startCall = r6.formattedScoreCall();
  r6.resolveFault('UPPER', 'OUT_OF_BOUNDS');
  const afterPointCall = r6.formattedScoreCall();
  const swappedIdx = r6.lowerRightPlayerIndex;
  const openSideOut = r6.resolveFault('LOWER', 'INTO_NET');
  const openCall = r6.formattedScoreCall();
  const toSrv2 = r6.resolveFault('UPPER', 'OUT_OF_BOUNDS');
  const srv2Call = r6.formattedScoreCall();
  const fullSideOut = r6.resolveFault('UPPER', 'KITCHEN_VOLLEY');
  const doublesOk =
    startCall === '0 - 0 - 2' &&
    afterPointCall === '1 - 0 - 2' &&
    swappedIdx === 1 &&
    openSideOut.sideOutOccurred &&
    openCall === '0 - 1 - 1' &&
    !toSrv2.sideOutOccurred &&
    srv2Call === '0 - 1 - 2' &&
    fullSideOut.sideOutOccurred &&
    r6.servingSide === 'LOWER' &&
    r6.serverNumberCall === 1;
  results.push({
    id: 'rules-doubles-rotation',
    name: 'Doubles 0-0-2 start, teammate side swap & server 1/2 rotation',
    passed: doublesOk,
    detail: '0-0-2 -> 1-0-2 (swap) -> side-out 0-1-1 -> 0-1-2 -> side-out',
  });

  return results;
}
