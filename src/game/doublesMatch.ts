import {
  BallPhysicsConstants,
  classifySwipe,
  computeBotReturnShot,
  computeShotVelocity,
  horizontalDragIntegral,
  stepBallPhysics,
  type BallState,
  type CharacterId,
  type HitFeedback,
  type PlayerEntity,
  type RallyPhase,
  type ShotType,
  type SimTickOutput,
  type SwipeVector,
  BOT_PROFILES,
  matchFormat,
} from './ballPhysics';
import { CourtSpec, type CourtSide, type SpecTestResult } from './courtSpec';
import { type FaultReason, type PointResolution, RulesEngine, type ScoringMode } from './rulesEngine';

/**
 * TypeScript mirror of core/sim/DoublesMatch.kt.
 * Runs 60 Hz 2v2 doubles with two-bounce, NVZ, server #1/#2 rotation,
 * and automatic left/right side switching via RulesEngine.
 */
export class DoublesMatch {
  static readonly PLAYER_SPEED_FT_S = 18.0;
  static readonly PARTNER_SPEED_FT_S = 15.2;
  static readonly BOT_SPEED_FT_S = 15.0;
  static readonly HIT_REACH_FT = 3.9;
  /** The human player's reach (arm + paddle); matches the drawn ring. AI players use HIT_REACH_FT. */
  static readonly PLAYER_REACH_FT = 4.3;
  static readonly MAX_HIT_HEIGHT_FT = 7.4;
  /** Auto-swing for the human player when the ball enters the reach ring (same as Solo). */
  autoSwingEnabled = true;
  private autoSwingPrevDist = Number.POSITIVE_INFINITY;
  static readonly POINT_PAUSE_SEC = 1.5;
  static readonly AI_SERVE_DELAY_SEC = 2.6;

  rules: RulesEngine;
  lowerTeam: [PlayerEntity, PlayerEntity];
  upperTeam: [PlayerEntity, PlayerEntity];
  ball: BallState;

  rallyPhase: RallyPhase = 'WAITING_SERVE';
  rallyHits = 0;
  bestRally = 0;
  lastCallout = '';
  lastShotLabel = '';
  lastFaultReason: FaultReason | null = null;
  aiServeTimerSec = 0;

  private accumulator = 0;
  private pauseTimerSec = 0;
  private partnerHitCooldownSec = 0;
  private upperHitCooldownSec = 0;

  constructor(
    playerChar: CharacterId = 'RIO',
    partnerChar: CharacterId = 'DILL',
    upperRightChar: CharacterId = 'LOLA',
    upperLeftChar: CharacterId = 'BERT',
    scoringMode: ScoringMode = 'SIDE_OUT',
  ) {
    this.rules = new RulesEngine(scoringMode, 'DOUBLES', 11, 2, 'LOWER');

    this.lowerTeam = [
      {
        side: 'LOWER',
        characterId: playerChar,
        x: CourtSpec.COURT_CENTER_X + 4.8,
        y: CourtSpec.COURT_BOTTOM_Y - 1.1,
        targetX: CourtSpec.COURT_CENTER_X + 4.8,
        targetY: CourtSpec.COURT_BOTTOM_Y - 1.1,
        swingTimer: 0,
        lastSwingType: 'DRIVE',
      },
      {
        side: 'LOWER',
        characterId: partnerChar,
        x: CourtSpec.COURT_CENTER_X - 4.8,
        y: CourtSpec.COURT_BOTTOM_Y - 1.4,
        targetX: CourtSpec.COURT_CENTER_X - 4.8,
        targetY: CourtSpec.COURT_BOTTOM_Y - 1.4,
        swingTimer: 0,
        lastSwingType: 'DRIVE',
      },
    ];

    this.upperTeam = [
      {
        side: 'UPPER',
        characterId: upperRightChar,
        x: CourtSpec.COURT_CENTER_X - 4.8,
        y: CourtSpec.COURT_TOP_Y + 1.1,
        targetX: CourtSpec.COURT_CENTER_X - 4.8,
        targetY: CourtSpec.COURT_TOP_Y + 1.1,
        swingTimer: 0,
        lastSwingType: 'DRIVE',
      },
      {
        side: 'UPPER',
        characterId: upperLeftChar,
        x: CourtSpec.COURT_CENTER_X + 4.8,
        y: CourtSpec.COURT_TOP_Y + 1.4,
        targetX: CourtSpec.COURT_CENTER_X + 4.8,
        targetY: CourtSpec.COURT_TOP_Y + 1.4,
        swingTimer: 0,
        lastSwingType: 'DRIVE',
      },
    ];

    this.ball = {
      x: this.lowerTeam[0].x - 0.9,
      y: this.lowerTeam[0].y + 1.3,
      z: 2.4,
      vx: 0,
      vy: 0,
      vz: 0,
      active: false,
      bouncesOnCurrentSide: 0,
      lastHitterSide: null,
      crossedNetSinceHit: false,
    };

    this.setupPointForServe();
  }

  get player(): PlayerEntity {
    return this.lowerTeam[0];
  }

  get partner(): PlayerEntity {
    return this.lowerTeam[1];
  }

  activeServerIndex(): number {
    return this.rules.currentServeAssignment().serverIndexInTeam;
  }

  activeServerSide(): CourtSide {
    return this.rules.servingSide;
  }

  resetMatch(scoringMode: ScoringMode = this.rules.scoringMode) {
    this.rules = new RulesEngine(scoringMode, 'DOUBLES', 11, 2, 'LOWER');
    this.rallyHits = 0;
    this.bestRally = 0;
    this.lastShotLabel = '';
    this.lastFaultReason = null;
    this.setupPointForServe();
  }

  setupPointForServe() {
    if (this.rules.isMatchOver) {
      this.rallyPhase = 'MATCH_OVER';
      this.lastCallout = this.rules.matchWinner === 'LOWER' ? 'Your Team Wins!' : 'Opponents Win!';
      return;
    }

    const assign = this.rules.currentServeAssignment();
    this.rallyPhase = 'WAITING_SERVE';
    this.rallyHits = 0;
    this.pauseTimerSec = 0;
    this.aiServeTimerSec =
      assign.servingSide === 'LOWER' && assign.serverIndexInTeam === 0
        ? 0
        : DoublesMatch.AI_SERVE_DELAY_SEC;
    this.partnerHitCooldownSec = 0;
    this.upperHitCooldownSec = 0;
    this.lastFaultReason = null;

    const lowerRightIdx = this.rules.lowerRightPlayerIndex;
    const lowerLeftIdx = 1 - lowerRightIdx;
    const upperRightIdx = this.rules.upperRightPlayerIndex;
    const upperLeftIdx = 1 - upperRightIdx;

    const lowerRightX = CourtSpec.COURT_CENTER_X + 4.8;
    const lowerLeftX = CourtSpec.COURT_CENTER_X - 4.8;
    const upperRightX = CourtSpec.COURT_CENTER_X - 4.8;
    const upperLeftX = CourtSpec.COURT_CENTER_X + 4.8;

    const receiverSide: CourtSide = assign.servingSide === 'LOWER' ? 'UPPER' : 'LOWER';

    this.positionEntity(
      this.lowerTeam[lowerRightIdx],
      lowerRightX,
      this.yForRole(
        'LOWER',
        assign.servingSide === 'LOWER',
        assign.servingSide === 'LOWER' && assign.serverIndexInTeam === lowerRightIdx,
        receiverSide === 'LOWER' && assign.targetBox === 'RIGHT',
      ),
    );
    this.positionEntity(
      this.lowerTeam[lowerLeftIdx],
      lowerLeftX,
      this.yForRole(
        'LOWER',
        assign.servingSide === 'LOWER',
        assign.servingSide === 'LOWER' && assign.serverIndexInTeam === lowerLeftIdx,
        receiverSide === 'LOWER' && assign.targetBox === 'LEFT',
      ),
    );
    this.positionEntity(
      this.upperTeam[upperRightIdx],
      upperRightX,
      this.yForRole(
        'UPPER',
        assign.servingSide === 'UPPER',
        assign.servingSide === 'UPPER' && assign.serverIndexInTeam === upperRightIdx,
        receiverSide === 'UPPER' && assign.targetBox === 'LEFT',
      ),
    );
    this.positionEntity(
      this.upperTeam[upperLeftIdx],
      upperLeftX,
      this.yForRole(
        'UPPER',
        assign.servingSide === 'UPPER',
        assign.servingSide === 'UPPER' && assign.serverIndexInTeam === upperLeftIdx,
        receiverSide === 'UPPER' && assign.targetBox === 'RIGHT',
      ),
    );

    const server =
      assign.servingSide === 'LOWER'
        ? this.lowerTeam[assign.serverIndexInTeam]
        : this.upperTeam[assign.serverIndexInTeam];

    this.ball.x = server.x + (assign.servingSide === 'LOWER' ? -0.9 : 0.9);
    this.ball.y = server.y + (assign.servingSide === 'LOWER' ? 1.3 : -1.3);
    this.ball.z = 2.4;
    this.ball.vx = 0;
    this.ball.vy = 0;
    this.ball.vz = 0;
    this.ball.active = false;
    this.ball.bouncesOnCurrentSide = 0;
    this.ball.lastHitterSide = null;
    this.ball.crossedNetSinceHit = false;

    if (assign.servingSide === 'LOWER' && assign.serverIndexInTeam === 0) {
      this.lastCallout = `Your serve (${this.rules.formattedScoreCall()})`;
    } else if (assign.servingSide === 'LOWER') {
      this.lastCallout = `Partner serve (${this.rules.formattedScoreCall()})`;
    } else {
      this.lastCallout = `Opponent serve (${this.rules.formattedScoreCall()})`;
    }
  }

  private yForRole(
    side: CourtSide,
    isServingTeam: boolean,
    isServer: boolean,
    isTargetBox: boolean,
  ): number {
    if (side === 'LOWER') {
      if (isServingTeam && isServer) return CourtSpec.COURT_BOTTOM_Y - 1.1;
      if (isServingTeam) return CourtSpec.COURT_BOTTOM_Y - 1.4;
      if (isTargetBox) return CourtSpec.COURT_BOTTOM_Y - 1.1;
      return CourtSpec.LOWER_NVZ_Y - 1.2;
    }
    if (isServingTeam && isServer) return CourtSpec.COURT_TOP_Y + 1.1;
    if (isServingTeam) return CourtSpec.COURT_TOP_Y + 1.4;
    if (isTargetBox) return CourtSpec.COURT_TOP_Y + 1.1;
    return CourtSpec.UPPER_NVZ_Y + 1.2;
  }

  private positionEntity(entity: PlayerEntity, x: number, y: number) {
    entity.x = x;
    entity.y = y;
    entity.targetX = x;
    entity.targetY = y;
    entity.swingTimer = 0;
  }

  setPlayerMoveTarget(worldX: number, worldY: number) {
    if (this.rallyPhase === 'MATCH_OVER') return;
    const maxY =
      this.rallyPhase === 'WAITING_SERVE' &&
      this.rules.servingSide === 'LOWER' &&
      this.activeServerIndex() === 0
        ? CourtSpec.COURT_BOTTOM_Y - 0.3
        : CourtSpec.NET_Y - 0.6;
    this.player.targetX = Math.max(1.5, Math.min(CourtSpec.WORLD_WIDTH_FT - 1.5, worldX));
    this.player.targetY = Math.max(1.5, Math.min(maxY, worldY));
  }

  /** Why the human can or can't hit right now (drives the ring color and hint). */

  playerHitState(): 'NONE' | 'READY' | 'WAIT_BOUNCE' | 'KITCHEN' | 'TOO_HIGH' {

    const ball = this.ball;

    if (this.rallyPhase !== 'RALLY_LIVE' || !ball.active || ball.lastHitterSide === 'LOWER') return 'NONE';

    const dist = Math.hypot(ball.x - this.player.x, ball.y - this.player.y);

    if (dist > DoublesMatch.PLAYER_REACH_FT || ball.y > CourtSpec.NET_Y + 1.2) return 'NONE';

    if (ball.z > DoublesMatch.MAX_HIT_HEIGHT_FT) return 'TOO_HIGH';

    if (ball.bouncesOnCurrentSide === 0) {

      if (this.rules.rallyShotCount === 1 || this.rules.rallyShotCount === 2) return 'WAIT_BOUNCE';

      if (CourtSpec.isInKitchen(this.player.x, this.player.y, 'LOWER')) return 'KITCHEN';

    }

    return 'READY';

  }


  canPlayerReachBall(): boolean {
    if (this.rallyPhase !== 'RALLY_LIVE' || !this.ball.active) return false;
    if (this.ball.lastHitterSide === 'LOWER') return false;
    if (this.ball.y > CourtSpec.NET_Y + 1.2) return false;
    const dist = Math.hypot(this.ball.x - this.player.x, this.ball.y - this.player.y);
    return dist <= DoublesMatch.PLAYER_REACH_FT && this.ball.z <= DoublesMatch.MAX_HIT_HEIGHT_FT;
  }

  launchServe(aimOffsetX = 0): { hit: HitFeedback | null; fault: PointResolution | null } {
    if (
      this.rallyPhase !== 'WAITING_SERVE' ||
      this.rules.servingSide !== 'LOWER' ||
      this.activeServerIndex() !== 0
    ) {
      return { hit: null, fault: null };
    }
    return this.executeServe('LOWER', this.player, aimOffsetX);
  }

  triggerReadyAiServe(): { hit: HitFeedback | null; fault: PointResolution | null } {
    if (this.rallyPhase !== 'WAITING_SERVE') return { hit: null, fault: null };
    const assign = this.rules.currentServeAssignment();
    if (assign.servingSide === 'LOWER' && assign.serverIndexInTeam === 0) {
      return { hit: null, fault: null };
    }
    const server =
      assign.servingSide === 'LOWER'
        ? this.lowerTeam[assign.serverIndexInTeam]
        : this.upperTeam[assign.serverIndexInTeam];
    this.aiServeTimerSec = 0;
    return this.executeServe(assign.servingSide, server, 0);
  }

  attemptPlayerSwipeHit(
    swipe: SwipeVector,
    forcedShotType?: ShotType,
  ): { hit: HitFeedback | null; fault: PointResolution | null } {
    if (
      this.rallyPhase === 'WAITING_SERVE' &&
      this.rules.servingSide === 'LOWER' &&
      this.activeServerIndex() === 0 &&
      swipe.dyFt > 1.0
    ) {
      return this.launchServe(swipe.dxFt * 0.45);
    }

    if (!this.canPlayerReachBall()) return { hit: null, fault: null };

    const fault = this.rules.validatePlayerHit(
      'LOWER',
      this.player.x,
      this.player.y,
      this.ball.bouncesOnCurrentSide,
    );
    if (fault) {
      return { hit: null, fault: this.applyFault('LOWER', fault) };
    }

    const shotType = forcedShotType ?? classifySwipe(swipe);
    const vel = computeShotVelocity(
      this.ball.x,
      this.ball.y,
      swipe,
      shotType,
      'UPPER',
      0.88,
      Math.random() * 2 - 1,
      this.ball.z,
    );

    this.ball.vx = vel.vx;
    this.ball.vy = vel.vy;
    this.ball.vz = vel.vz;
    this.ball.lastHitterSide = 'LOWER';
    this.ball.crossedNetSinceHit = false;
    this.ball.bouncesOnCurrentSide = 0;
    this.player.swingTimer = 0.24;
    this.player.lastSwingType = shotType;
    this.bestRally = Math.max(this.bestRally, this.rules.rallyShotCount);
    this.rallyHits = this.rules.rallyShotCount >= 3 ? this.rules.rallyShotCount : 0;
    this.lastShotLabel = shotType;

    return {
      hit: {
        hitterSide: 'LOWER',
        shotType,
        timingAccuracy: 0.88,
        hitX: this.ball.x,
        hitY: this.ball.y,
      },
      fault: null,
    };
  }

  update(rawDeltaSec: number): SimTickOutput[] {
    const clamped = Math.min(rawDeltaSec, 0.05);
    this.accumulator += clamped;
    const outputs: SimTickOutput[] = [];

    while (this.accumulator >= CourtSpec.FIXED_TIMESTEP_SEC) {
      outputs.push(this.stepFixed(CourtSpec.FIXED_TIMESTEP_SEC));
      this.accumulator -= CourtSpec.FIXED_TIMESTEP_SEC;
    }
    return outputs;
  }

  private stepFixed(dt: number): SimTickOutput {
    this.moveTowardTarget(this.player, DoublesMatch.PLAYER_SPEED_FT_S, dt);
    this.moveTowardTarget(this.partner, DoublesMatch.PARTNER_SPEED_FT_S, dt);
    const oppSpeed = Math.max(10, BOT_PROFILES[matchFormat.difficulty].speedFtS + 0.8);
    this.moveTowardTarget(this.upperTeam[0], oppSpeed, dt);
    this.moveTowardTarget(this.upperTeam[1], oppSpeed, dt);

    for (const p of [...this.lowerTeam, ...this.upperTeam]) {
      if (p.swingTimer > 0) p.swingTimer = Math.max(0, p.swingTimer - dt);
    }
    if (this.partnerHitCooldownSec > 0) {
      this.partnerHitCooldownSec = Math.max(0, this.partnerHitCooldownSec - dt);
    }
    if (this.upperHitCooldownSec > 0) {
      this.upperHitCooldownSec = Math.max(0, this.upperHitCooldownSec - dt);
    }

    const idleEvents = {
      bounced: false,
      bounceX: this.ball.x,
      bounceY: this.ball.y,
      bounceZSpeed: 0,
      netOutcome: 'NONE' as const,
      crossedNetThisStep: false,
    };

    if (this.rallyPhase === 'MATCH_OVER') {
      return { stepEvents: idleEvents, hitFeedback: null, pointResolution: null };
    }

    if (this.rallyPhase === 'POINT_PAUSE') {
      this.pauseTimerSec -= dt;
      if (this.pauseTimerSec <= 0) {
        this.setupPointForServe();
      }
      return { stepEvents: idleEvents, hitFeedback: null, pointResolution: null };
    }

    if (this.rallyPhase === 'WAITING_SERVE') {
      const assign = this.rules.currentServeAssignment();
      const server =
        assign.servingSide === 'LOWER'
          ? this.lowerTeam[assign.serverIndexInTeam]
          : this.upperTeam[assign.serverIndexInTeam];
      this.ball.x = server.x + (assign.servingSide === 'LOWER' ? -0.9 : 0.9);
      this.ball.y = server.y + (assign.servingSide === 'LOWER' ? 1.3 : -1.3);
      this.ball.z = 2.4;

      if (!(assign.servingSide === 'LOWER' && assign.serverIndexInTeam === 0)) {
        this.aiServeTimerSec -= dt;
        if (this.aiServeTimerSec <= 0) {
          const res = this.executeServe(assign.servingSide, server, 0);
          return { stepEvents: idleEvents, hitFeedback: res.hit, pointResolution: res.fault };
        }
      }
      return { stepEvents: idleEvents, hitFeedback: null, pointResolution: null };
    }

    this.updateDoublesAiPositioning();


    let hitFeedback: HitFeedback | null = null;
    if (this.autoSwingEnabled && this.canPlayerReachBall()) {
      const dist = Math.hypot(this.ball.x - this.player.x, this.ball.y - this.player.y);
      const isVolley = this.ball.bouncesOnCurrentSide === 0;
      const wouldFault =
        isVolley &&
        (this.rules.rallyShotCount === 1 ||
          this.rules.rallyShotCount === 2 ||
          CourtSpec.isInKitchen(this.player.x, this.player.y, 'LOWER'));
      const leaving = dist > this.autoSwingPrevDist;
      this.autoSwingPrevDist = dist;
      if (!wouldFault && (dist <= DoublesMatch.PLAYER_REACH_FT * 0.7 || leaving)) {
        const res = this.attemptPlayerSwipeHit({ dxFt: 0, dyFt: 6, durationSec: 0.15 }, 'DRIVE');
        this.autoSwingPrevDist = Number.POSITIVE_INFINITY;
        if (res.fault) return { stepEvents: idleEvents, hitFeedback: null, pointResolution: res.fault };
        hitFeedback = res.hit;
      }
    } else {
      this.autoSwingPrevDist = Number.POSITIVE_INFINITY;
    }

    const stepEvents = stepBallPhysics(this.ball, dt);
    let pointResolution: PointResolution | null = null;

    if (stepEvents.netOutcome === 'HIT_NET') {
      pointResolution = this.applyFault(this.ball.lastHitterSide ?? 'LOWER', 'INTO_NET');
      return { stepEvents, hitFeedback, pointResolution };
    }

    if (stepEvents.bounced) {
      if (this.ball.bouncesOnCurrentSide === 1) {
        const bounceFault = this.rules.validateFirstBounce(
          stepEvents.bounceX,
          stepEvents.bounceY,
          this.ball.lastHitterSide,
        );
        if (bounceFault) {
          pointResolution = this.applyFault(this.ball.lastHitterSide ?? 'LOWER', bounceFault);
          return { stepEvents, hitFeedback, pointResolution };
        }
      }
      if (this.ball.bouncesOnCurrentSide >= 2) {
        const receivingSide: CourtSide = this.ball.lastHitterSide === 'LOWER' ? 'UPPER' : 'LOWER';
        pointResolution = this.applyFault(receivingSide, 'DOUBLE_BOUNCE');
        return { stepEvents, hitFeedback, pointResolution };
      }
    }

    if (
      this.ball.x < -2 ||
      this.ball.x > CourtSpec.WORLD_WIDTH_FT + 2 ||
      this.ball.y < -3 ||
      this.ball.y > CourtSpec.WORLD_HEIGHT_FT + 3
    ) {
      if (this.ball.crossedNetSinceHit && this.ball.bouncesOnCurrentSide >= 1) {
        const receivingSide: CourtSide = this.ball.lastHitterSide === 'LOWER' ? 'UPPER' : 'LOWER';
        pointResolution = this.applyFault(receivingSide, 'DOUBLE_BOUNCE');
        return { stepEvents, hitFeedback, pointResolution };
      }
      const faultSide = this.ball.lastHitterSide ?? 'LOWER';
      const fault: FaultReason = this.rules.rallyShotCount === 1 ? 'SERVE_OUT_OF_BOX' : 'OUT_OF_BOUNDS';
      pointResolution = this.applyFault(faultSide, fault);
      return { stepEvents, hitFeedback, pointResolution };
    }

    // AI Partner return on LOWER side
    if (
      this.ball.lastHitterSide === 'UPPER' &&
      this.ball.crossedNetSinceHit &&
      this.partnerHitCooldownSec <= 0 &&
      (this.rules.rallyShotCount > 2 || this.ball.bouncesOnCurrentSide >= 1)
    ) {
      const dPartner = Math.hypot(this.ball.x - this.partner.x, this.ball.y - this.partner.y);
      const dHuman = Math.hypot(this.ball.x - this.player.x, this.ball.y - this.player.y);
      if (
        dPartner <= DoublesMatch.HIT_REACH_FT &&
        (this.isOnPartnerSide(this.ball.x) || dPartner + 1.5 < dHuman) &&
        this.ball.z <= DoublesMatch.MAX_HIT_HEIGHT_FT
      ) {
        const fault = this.rules.validatePlayerHit(
          'LOWER',
          this.partner.x,
          this.partner.y,
          this.ball.bouncesOnCurrentSide,
        );
        if (fault) {
          pointResolution = this.applyFault('LOWER', fault);
          return { stepEvents, hitFeedback, pointResolution };
        }
        hitFeedback = this.launchAiShot(this.partner, 'UPPER');
        this.partnerHitCooldownSec = 0.42;
      }
    }

    // Upper AI opponents return
    if (
      this.ball.lastHitterSide === 'LOWER' &&
      this.ball.crossedNetSinceHit &&
      this.upperHitCooldownSec <= 0 &&
      (this.rules.rallyShotCount > 2 || this.ball.bouncesOnCurrentSide >= 1)
    ) {
      const d0 = Math.hypot(this.ball.x - this.upperTeam[0].x, this.ball.y - this.upperTeam[0].y);
      const d1 = Math.hypot(this.ball.x - this.upperTeam[1].x, this.ball.y - this.upperTeam[1].y);
      const responder = d0 <= d1 ? this.upperTeam[0] : this.upperTeam[1];
      const dist = Math.min(d0, d1);
      if (dist <= DoublesMatch.HIT_REACH_FT && this.ball.z <= DoublesMatch.MAX_HIT_HEIGHT_FT) {
        const fault = this.rules.validatePlayerHit(
          'UPPER',
          responder.x,
          responder.y,
          this.ball.bouncesOnCurrentSide,
        );
        if (fault) {
          pointResolution = this.applyFault('UPPER', fault);
          return { stepEvents, hitFeedback, pointResolution };
        }
        hitFeedback = this.launchAiShot(responder, 'LOWER');
        this.upperHitCooldownSec = 0.42;
      }
    }

    return { stepEvents, hitFeedback, pointResolution };
  }

  private executeServe(
    serverSide: CourtSide,
    server: PlayerEntity,
    aimOffsetX: number,
  ): { hit: HitFeedback | null; fault: PointResolution | null } {
    const assign = this.rules.currentServeAssignment();
    const footFault = this.rules.beginServe(server.x, server.y);
    if (footFault) {
      return { hit: null, fault: this.applyFault(serverSide, footFault) };
    }

    const targetX =
      assign.targetBox === 'LEFT' ? CourtSpec.COURT_LEFT_X + 5.0 : CourtSpec.COURT_RIGHT_X - 5.0;
    const targetY =
      assign.targetCourtSide === 'UPPER'
        ? CourtSpec.UPPER_NVZ_Y + 7.8
        : CourtSpec.LOWER_NVZ_Y - 7.8;

    const tof = 0.98;
    const invIntegral = 1 / horizontalDragIntegral(tof);
    const offset = Math.max(-2.5, Math.min(2.5, aimOffsetX));
    this.ball.vx = (targetX + offset - this.ball.x) * invIntegral;
    this.ball.vy = (targetY - this.ball.y) * invIntegral;
    this.ball.vz = -0.5 * BallPhysicsConstants.GRAVITY_FT_S2 * tof - this.ball.z / tof;
    this.ball.active = true;
    this.ball.lastHitterSide = serverSide;
    this.ball.crossedNetSinceHit = false;
    this.ball.bouncesOnCurrentSide = 0;

    server.swingTimer = 0.24;
    this.rallyPhase = 'RALLY_LIVE';
    this.aiServeTimerSec = 0;
    this.rallyHits = 0;
    this.bestRally = Math.max(this.bestRally, 1);
    this.lastShotLabel = 'Serve';
    this.lastCallout = '';

    return {
      hit: {
        hitterSide: serverSide,
        shotType: 'SERVE',
        timingAccuracy: 0.95,
        hitX: this.ball.x,
        hitY: this.ball.y,
      },
      fault: null,
    };
  }

  private launchAiShot(hitter: PlayerEntity, targetSide: CourtSide): HitFeedback {
    const defenders = targetSide === 'UPPER' ? this.upperTeam : this.lowerTeam;
    const { shotType, vx, vy, vz } = computeBotReturnShot({
      fromX: this.ball.x,
      fromY: this.ball.y,
      fromZ: this.ball.z,
      hitterY: hitter.y,
      targetSide,
      opponentXs: [defenders[0].x, defenders[1].x],
      difficulty: targetSide === 'LOWER' ? matchFormat.difficulty : 'NORMAL',
      aimAwayChance: targetSide === 'LOWER' ? BOT_PROFILES[matchFormat.difficulty].aimAwayChance : 1,
      sidelineMarginFt: targetSide === 'LOWER' ? BOT_PROFILES[matchFormat.difficulty].sidelineMarginFt : 2.0,
    });

    this.ball.vx = vx;
    this.ball.vy = vy;
    this.ball.vz = vz;
    this.ball.lastHitterSide = targetSide === 'UPPER' ? 'LOWER' : 'UPPER';
    this.ball.crossedNetSinceHit = false;
    this.ball.bouncesOnCurrentSide = 0;
    hitter.swingTimer = 0.24;
    hitter.lastSwingType = shotType;
    this.bestRally = Math.max(this.bestRally, this.rules.rallyShotCount);
    this.rallyHits = this.rules.rallyShotCount >= 3 ? this.rules.rallyShotCount : 0;
    this.lastShotLabel = shotType;

    return {
      hitterSide: this.ball.lastHitterSide,
      shotType,
      timingAccuracy: 0.88,
      hitX: this.ball.x,
      hitY: this.ball.y,
    };
  }

    /**
   * Where a chasing AI should run: on the ball's path, and if the ball hasn't bounced yet,
   * just behind its predicted bounce so it meets the ball after the bounce (avoids rushing the
   * net while the ball sails over and double-bounces behind them).
   */
  /** The AI partner covers the half opposite the human; balls on the human's half are theirs. */
  private isOnPartnerSide(x: number): boolean {
    const partnerCoversLeft = this.player.x >= CourtSpec.COURT_CENTER_X;
    return partnerCoversLeft ? x < CourtSpec.COURT_CENTER_X : x >= CourtSpec.COURT_CENTER_X;
  }

  private chasePoint(side: CourtSide): { x: number; y: number } {
    const b = this.ball;
    if (b.bouncesOnCurrentSide > 0 || Math.abs(b.vy) < 0.5) return { x: b.x, y: b.y };
    const g = BallPhysicsConstants.GRAVITY_FT_S2;
    const t = (-b.vz - Math.sqrt(Math.max(0, b.vz * b.vz - 2 * g * b.z))) / g;
    const landX = b.x + b.vx * t;
    const landY = b.y + b.vy * t;
    const backOff = 2.2;
    const dirY = side === 'UPPER' ? 1 : -1;
    return {
      x: landX + (b.vx / Math.max(1, Math.abs(b.vy))) * backOff,
      y: landY + dirY * backOff,
    };
  }

  private updateDoublesAiPositioning() {
    const partnerIdealX =
      this.player.x >= CourtSpec.COURT_CENTER_X
        ? CourtSpec.COURT_CENTER_X - 4.6
        : CourtSpec.COURT_CENTER_X + 4.6;
    const partnerIdealY =
      this.rules.rallyShotCount <= 2 ? CourtSpec.COURT_BOTTOM_Y - 1.2 : CourtSpec.LOWER_NVZ_Y - 1.0;

    if (this.ball.lastHitterSide === 'UPPER' && this.ball.y < CourtSpec.NET_Y + 4) {
      const cp = this.chasePoint('LOWER');
      const distPartnerToCp = Math.hypot(cp.x - this.partner.x, cp.y - this.partner.y);
      const distHumanToCp = Math.hypot(cp.x - this.player.x, cp.y - this.player.y);
      if (this.isOnPartnerSide(cp.x) || distPartnerToCp + 2.0 < distHumanToCp) {
        this.partner.targetX = Math.max(
          CourtSpec.COURT_LEFT_X + 1.2,
          Math.min(CourtSpec.COURT_RIGHT_X - 1.2, cp.x),
        );
        this.partner.targetY = Math.max(2.0, Math.min(CourtSpec.LOWER_NVZ_Y - 0.6, cp.y));
      } else {
        this.partner.targetX = partnerIdealX;
        this.partner.targetY = partnerIdealY;
      }
    } else {
      this.partner.targetX = partnerIdealX;
      this.partner.targetY = partnerIdealY;
    }

    const upperBaseY =
      this.rules.rallyShotCount <= 2 ? CourtSpec.COURT_TOP_Y + 1.4 : CourtSpec.UPPER_NVZ_Y + 1.1;
    const u0TargetX = CourtSpec.COURT_CENTER_X - 4.6;
    const u1TargetX = CourtSpec.COURT_CENTER_X + 4.6;

    if (this.ball.lastHitterSide === 'LOWER' && this.ball.y > CourtSpec.NET_Y - 4) {

      const cp = this.chasePoint('UPPER');
      const chase0 =
        Math.abs(this.ball.x - this.upperTeam[0].x) <= Math.abs(this.ball.x - this.upperTeam[1].x);
      if (chase0) {
        this.upperTeam[0].targetX = Math.max(
          CourtSpec.COURT_LEFT_X + 1.0,
          Math.min(CourtSpec.COURT_RIGHT_X - 1.0, cp.x),
        );
        this.upperTeam[0].targetY = Math.max(
          CourtSpec.UPPER_NVZ_Y + 0.6,
          Math.min(CourtSpec.WORLD_HEIGHT_FT - 2.0, cp.y),
        );
        this.upperTeam[1].targetX = u1TargetX;
        this.upperTeam[1].targetY = upperBaseY;
      } else {
        this.upperTeam[0].targetX = u0TargetX;
        this.upperTeam[0].targetY = upperBaseY;
        this.upperTeam[1].targetX = Math.max(
          CourtSpec.COURT_LEFT_X + 1.0,
          Math.min(CourtSpec.COURT_RIGHT_X - 1.0, cp.x),
        );
        this.upperTeam[1].targetY = Math.max(
          CourtSpec.UPPER_NVZ_Y + 0.6,
          Math.min(CourtSpec.WORLD_HEIGHT_FT - 2.0, cp.y),
        );
      }
    } else {
      this.upperTeam[0].targetX = u0TargetX;
      this.upperTeam[0].targetY = upperBaseY;
      this.upperTeam[1].targetX = u1TargetX;
      this.upperTeam[1].targetY = upperBaseY;
    }
  }

  private applyFault(faultingSide: CourtSide, reason: FaultReason): PointResolution | null {
    if (this.rallyPhase === 'POINT_PAUSE' || this.rallyPhase === 'MATCH_OVER') {
      return null;
    }
    const resolution = this.rules.resolveFault(faultingSide, reason);
    this.lastCallout = resolution.calloutText;
    this.lastFaultReason = reason;
    this.rallyPhase = resolution.matchWinner !== null ? 'MATCH_OVER' : 'POINT_PAUSE';
    this.pauseTimerSec = DoublesMatch.POINT_PAUSE_SEC;
    this.ball.active = false;
    return resolution;
  }

  private moveTowardTarget(entity: PlayerEntity, speed: number, dt: number) {
    const dx = entity.targetX - entity.x;
    const dy = entity.targetY - entity.y;
    const dist = Math.hypot(dx, dy);
    if (dist < 0.04) {
      entity.x = entity.targetX;
      entity.y = entity.targetY;
      return;
    }
    const step = Math.min(dist, speed * dt);
    entity.x += (dx / dist) * step;
    entity.y += (dy / dist) * step;
  }
}

export function runDoublesMatchTests(): SpecTestResult[] {
  const m1 = new DoublesMatch('RIO', 'DILL', 'LOLA', 'BERT', 'SIDE_OUT');
  const startCallOk =
    m1.rules.formattedScoreCall() === '0 - 0 - 2' &&
    m1.lowerTeam[0].x > CourtSpec.COURT_CENTER_X &&
    m1.lowerTeam[1].x < CourtSpec.COURT_CENTER_X &&
    m1.upperTeam[0].x < CourtSpec.COURT_CENTER_X &&
    m1.upperTeam[1].x > CourtSpec.COURT_CENTER_X;

  m1.rules.applyRallyOutcome('LOWER', 'OUT_OF_BOUNDS');
  m1.setupPointForServe();
  const swapOk =
    m1.rules.scoreLower === 1 &&
    m1.rules.lowerRightPlayerIndex === 1 &&
    m1.activeServerIndex() === 0 &&
    m1.lowerTeam[0].x < CourtSpec.COURT_CENTER_X &&
    m1.lowerTeam[1].x > CourtSpec.COURT_CENTER_X;

  return [
    {
      id: 'doubles-2v2-start',
      name: '2v2 Doubles starts 0-0-2 with four positioned players',
      passed: startCallOk,
      detail: `Call "${m1.rules.formattedScoreCall()}" (opening server #2) with split left/right court boxes`,
    },
    {
      id: 'doubles-2v2-swap',
      name: 'Doubles scoring swaps teammate sides and retains server',
      passed: swapOk,
      detail: `lowerRightPlayerIndex=${m1.rules.lowerRightPlayerIndex}, lowerTeam[0].x=${m1.lowerTeam[0].x.toFixed(1)} ft`,
    },
  ];
}
