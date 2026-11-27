import {
  BallPhysicsConstants,
  classifySwipe,
  computeShotVelocity,
  stepBallPhysics,
  type BallState,
  type CharacterId,
  type HitFeedback,
  type PlayerEntity,
  type RallyPhase,
  type StepEvents,
  type SwipeVector,
} from './ballPhysics';
import { CourtSpec, type CourtSide, type SpecTestResult } from './courtSpec';
import { RulesEngine, type FaultReason, type PointResolution, type ScoringMode } from './rulesEngine';

export type RoomStatus = 'WAITING' | 'PLAYING' | 'HOST_LEFT' | 'FINISHED';
export type RoomMode = 'SINGLES_1V1' | 'DOUBLES_2V2';

export interface PlayerInputSnapshot {
  uid: string;
  sequence: number;
  targetX: number;
  targetY: number;
  swingType: string | null;
  aimX: number;
  aimY: number;
  swipeDurationSec: number;
  timestampMs: number;
}

export interface MatchStateSnapshot {
  tick: number;
  ballX: number;
  ballY: number;
  ballZ: number;
  ballVx: number;
  ballVy: number;
  ballVz: number;
  ballActive: boolean;
  hostX: number;
  hostY: number;
  guestX: number;
  guestY: number;
  hostSwingTimer: number;
  guestSwingTimer: number;
  scoreHost: number;
  scoreGuest: number;
  servingSide: 'HOST' | 'GUEST';
  rallyShotCount: number;
  rallyHits: number;
  lastEvent: string | null;
  matchWinner: 'HOST' | 'GUEST' | null;
  publishedAtMs: number;
}

export const RoomCodeGenerator = {
  roomRegex: /^PKL-[0-9]{3}$/,
  generate(): string {
    const code = Math.floor(100 + Math.random() * 900);
    return `PKL-${code}`;
  },
  isValid(code: string): boolean {
    return this.roomRegex.test(code.trim().toUpperCase());
  },
};

export interface OnlineStepResult {
  snapshotToPublish: MatchStateSnapshot | null;
  stepEvents: StepEvents;
  hitFeedback: HitFeedback | null;
  pointResolution: PointResolution | null;
}

export class OnlineMatch1v1 {
  static readonly PUBLISH_INTERVAL_SEC = 0.05; // 20 Hz state sync
  static readonly HOST_PLAYER_SPEED_FT_S = 18.5;
  static readonly LOCAL_HIT_REACH_FT = 3.9;
  static readonly REMOTE_GENEROUS_REACH_FT = 4.75;
  static readonly MAX_HIT_HEIGHT_FT = 6.0;
  static readonly POINT_PAUSE_SEC = 1.6;

  static mirrorX(x: number): number {
    return CourtSpec.WORLD_WIDTH_FT - x;
  }

  static mirrorY(y: number): number {
    return CourtSpec.WORLD_HEIGHT_FT - y;
  }

  readonly isHost: boolean;
  readonly localUid: string;
  readonly rules: RulesEngine;

  localPlayer: PlayerEntity;
  remotePlayer: PlayerEntity;
  ball: BallState;

  rallyPhase: RallyPhase = 'WAITING_SERVE';
  rallyHits = 0;
  bestRally = 0;
  lastCallout = 'Waiting for opponent...';
  lastShotLabel = '';
  lastFaultReason: FaultReason | null = null;
  hostDisconnected = false;

  scoreLocal = 0;
  scoreRemote = 0;
  servingLocal: boolean;
  matchWinnerLocal: boolean | null = null;

  private tickCounter = 0;
  private accumulator = 0;
  private publishAccumulator = 0;
  private pauseTimerSec = 0;
  private lastProcessedGuestSwingSeq = -1;
  private inputSeqCounter = 0;

  private interpBallX = 14;
  private interpBallY = 49;
  private interpBallZ = 2.4;
  private interpRemoteX = CourtSpec.COURT_CENTER_X - 4.5;
  private interpRemoteY = CourtSpec.COURT_TOP_Y + 1.1;

  private pendingGuestSwipe: SwipeVector | null = null;
  private lastPointBounce = false;

  constructor(
    isHost: boolean,
    localUid: string,
    localChar: CharacterId = 'RIO',
    remoteChar: CharacterId = 'LOLA',
    scoringMode: ScoringMode = 'RALLY',
  ) {
    this.isHost = isHost;
    this.localUid = localUid;
    this.rules = new RulesEngine(scoringMode, 'SINGLES');
    this.servingLocal = isHost;

    this.localPlayer = {
      side: 'LOWER',
      characterId: localChar,
      x: CourtSpec.COURT_CENTER_X + 4.5,
      y: CourtSpec.COURT_BOTTOM_Y - 1.1,
      targetX: CourtSpec.COURT_CENTER_X + 4.5,
      targetY: CourtSpec.COURT_BOTTOM_Y - 1.1,
      swingTimer: 0,
      lastSwingType: 'DRIVE',
    };

    this.remotePlayer = {
      side: 'UPPER',
      characterId: remoteChar,
      x: CourtSpec.COURT_CENTER_X - 4.5,
      y: CourtSpec.COURT_TOP_Y + 1.1,
      targetX: CourtSpec.COURT_CENTER_X - 4.5,
      targetY: CourtSpec.COURT_TOP_Y + 1.1,
      swingTimer: 0,
      lastSwingType: 'DRIVE',
    };

    this.ball = {
      x: 14,
      y: 7,
      z: 2.4,
      vx: 0,
      vy: 0,
      vz: 0,
      active: false,
      bouncesOnCurrentSide: 0,
      lastHitterSide: null,
      crossedNetSinceHit: false,
    };

    if (isHost) {
      this.setupPointForServe();
    }
  }

  setupPointForServe() {
    if (!this.isHost) return;
    if (this.rules.isMatchOver) {
      this.rallyPhase = 'MATCH_OVER';
      this.matchWinnerLocal = this.rules.matchWinner === 'LOWER';
      this.lastCallout = this.matchWinnerLocal ? 'You Win!' : 'Opponent Wins!';
      return;
    }

    const assign = this.rules.currentServeAssignment();
    this.rallyPhase = 'WAITING_SERVE';
    this.rallyHits = 0;
    this.lastFaultReason = null;
    this.scoreLocal = this.rules.scoreLower;
    this.scoreRemote = this.rules.scoreUpper;
    this.servingLocal = assign.servingSide === 'LOWER';

    const lowerX =
      assign.servingSide === 'LOWER' && assign.serverBox === 'LEFT'
        ? CourtSpec.COURT_CENTER_X - 4.5
        : assign.servingSide === 'LOWER'
          ? CourtSpec.COURT_CENTER_X + 4.5
          : assign.targetBox === 'LEFT'
            ? CourtSpec.COURT_CENTER_X - 4.5
            : CourtSpec.COURT_CENTER_X + 4.5;

    const upperX =
      assign.servingSide === 'UPPER' && assign.serverBox === 'LEFT'
        ? CourtSpec.COURT_CENTER_X - 4.5
        : assign.servingSide === 'UPPER'
          ? CourtSpec.COURT_CENTER_X + 4.5
          : assign.targetBox === 'LEFT'
            ? CourtSpec.COURT_CENTER_X - 4.5
            : CourtSpec.COURT_CENTER_X + 4.5;

    this.localPlayer.x = lowerX;
    this.localPlayer.y = CourtSpec.COURT_BOTTOM_Y - 1.1;
    this.localPlayer.targetX = this.localPlayer.x;
    this.localPlayer.targetY = this.localPlayer.y;
    this.localPlayer.swingTimer = 0;

    this.remotePlayer.x = upperX;
    this.remotePlayer.y = CourtSpec.COURT_TOP_Y + 1.1;
    this.remotePlayer.targetX = this.remotePlayer.x;
    this.remotePlayer.targetY = this.remotePlayer.y;
    this.remotePlayer.swingTimer = 0;

    if (this.servingLocal) {
      this.ball.x = this.localPlayer.x - 1.0;
      this.ball.y = this.localPlayer.y + 1.3;
      this.lastCallout = `Your serve (${this.rules.formattedScoreCall()})`;
    } else {
      this.ball.x = this.remotePlayer.x + 1.0;
      this.ball.y = this.remotePlayer.y - 1.3;
      this.lastCallout = `Opponent serving (${this.rules.formattedScoreCall()})`;
    }

    this.ball.z = 2.4;
    this.ball.vx = 0;
    this.ball.vy = 0;
    this.ball.vz = 0;
    this.ball.active = false;
    this.ball.bouncesOnCurrentSide = 0;
    this.ball.lastHitterSide = null;
    this.ball.crossedNetSinceHit = false;
  }

  canLocalReachBall(): boolean {
    if (this.rallyPhase !== 'RALLY_LIVE' || !this.ball.active) return false;
    if (this.isHost && this.ball.lastHitterSide === 'LOWER') return false;
    const dist = Math.hypot(this.ball.x - this.localPlayer.x, this.ball.y - this.localPlayer.y);
    return (
      dist <= OnlineMatch1v1.LOCAL_HIT_REACH_FT &&
      this.ball.z <= OnlineMatch1v1.MAX_HIT_HEIGHT_FT &&
      this.ball.y <= CourtSpec.NET_Y + 0.8
    );
  }

  setLocalMoveTarget(worldX: number, worldY: number): PlayerInputSnapshot {
    const maxY =
      this.rallyPhase === 'WAITING_SERVE' && this.servingLocal
        ? CourtSpec.COURT_BOTTOM_Y - 0.3
        : CourtSpec.NET_Y - 0.6;
    this.localPlayer.targetX = Math.max(1.5, Math.min(CourtSpec.WORLD_WIDTH_FT - 1.5, worldX));
    this.localPlayer.targetY = Math.max(1.5, Math.min(maxY, worldY));

    this.inputSeqCounter += 1;
    return {
      uid: this.localUid,
      sequence: this.inputSeqCounter,
      targetX: this.localPlayer.targetX,
      targetY: this.localPlayer.targetY,
      swingType: null,
      aimX: 0,
      aimY: 0,
      swipeDurationSec: 0.2,
      timestampMs: Date.now(),
    };
  }

  triggerLocalSwipe(swipe: SwipeVector): {
    guestInput: PlayerInputSnapshot | null;
    hit: HitFeedback | null;
    fault: PointResolution | null;
  } {
    if (this.isHost) {
      const res = this.handleHostSwipe(swipe);
      return { guestInput: null, hit: res.hit, fault: res.fault };
    }

    this.inputSeqCounter += 1;
    const shotType =
      this.rallyPhase === 'WAITING_SERVE' && this.servingLocal
        ? 'SERVE'
        : classifySwipe(swipe);
    this.localPlayer.swingTimer = 0.22;
    this.localPlayer.lastSwingType = shotType;

    return {
      guestInput: {
        uid: this.localUid,
        sequence: this.inputSeqCounter,
        targetX: this.localPlayer.targetX,
        targetY: this.localPlayer.targetY,
        swingType: shotType,
        aimX: swipe.dxFt,
        aimY: swipe.dyFt,
        swipeDurationSec: swipe.durationSec,
        timestampMs: Date.now(),
      },
      hit: {
        hitterSide: 'LOWER',
        shotType,
        timingAccuracy: 0.9,
        hitX: this.ball.x,
        hitY: this.ball.y,
      },
      fault: null,
    };
  }

  applyRemoteGuestInput(input: PlayerInputSnapshot) {
    if (!this.isHost) return;
    this.remotePlayer.targetX = Math.max(
      1.5,
      Math.min(CourtSpec.WORLD_WIDTH_FT - 1.5, OnlineMatch1v1.mirrorX(input.targetX)),
    );
    this.remotePlayer.targetY = Math.max(
      CourtSpec.NET_Y + 0.6,
      Math.min(CourtSpec.WORLD_HEIGHT_FT - 1.5, OnlineMatch1v1.mirrorY(input.targetY)),
    );

    if (input.swingType && input.sequence > this.lastProcessedGuestSwingSeq) {
      this.lastProcessedGuestSwingSeq = input.sequence;
      this.pendingGuestSwipe = {
        dxFt: -input.aimX,
        dyFt: -input.aimY,
        durationSec: input.swipeDurationSec,
      };
    }
  }

  applyHostSnapshot(snapshot: MatchStateSnapshot) {
    if (this.isHost) return;

    this.interpBallX = OnlineMatch1v1.mirrorX(snapshot.ballX);
    this.interpBallY = OnlineMatch1v1.mirrorY(snapshot.ballY);
    this.interpBallZ = snapshot.ballZ;
    this.ball.active = snapshot.ballActive;

    this.interpRemoteX = OnlineMatch1v1.mirrorX(snapshot.hostX);
    this.interpRemoteY = OnlineMatch1v1.mirrorY(snapshot.hostY);
    this.remotePlayer.swingTimer = snapshot.hostSwingTimer;
    this.localPlayer.swingTimer = Math.max(this.localPlayer.swingTimer, snapshot.guestSwingTimer);

    this.scoreLocal = snapshot.scoreGuest;
    this.scoreRemote = snapshot.scoreHost;
    this.servingLocal = snapshot.servingSide === 'GUEST';
    this.rallyHits = snapshot.rallyHits;
    this.bestRally = Math.max(this.bestRally, snapshot.rallyShotCount, this.rallyHits);
    this.lastCallout = snapshot.lastEvent ?? '';

    this.matchWinnerLocal =
      snapshot.matchWinner === 'GUEST'
        ? true
        : snapshot.matchWinner === 'HOST'
          ? false
          : null;

    const eventText = snapshot.lastEvent ?? '';
    this.rallyPhase =
      this.matchWinnerLocal !== null
        ? 'MATCH_OVER'
        : !snapshot.ballActive &&
            snapshot.rallyShotCount === 0 &&
            (eventText.includes('fault') ||
              eventText.includes('Out') ||
              eventText.includes('Net') ||
              eventText.includes('bounce'))
          ? 'POINT_PAUSE'
          : !snapshot.ballActive
            ? 'WAITING_SERVE'
            : 'RALLY_LIVE';
  }

  onRoomMetaUpdated(status: RoomStatus) {
    if (!this.isHost && status === 'HOST_LEFT') {
      this.hostDisconnected = true;
      this.rallyPhase = 'MATCH_OVER';
      this.lastCallout = 'Host disconnected';
    }
  }

  update(rawDeltaSec: number): OnlineStepResult {
    const noEvents: StepEvents = {
      bounced: false,
      bounceZSpeed: 0,
      bounceX: this.ball.x,
      bounceY: this.ball.y,
      netOutcome: 'NONE',
    };

    if (this.hostDisconnected) {
      return {
        snapshotToPublish: null,
        stepEvents: noEvents,
        hitFeedback: null,
        pointResolution: null,
      };
    }

    const clampedDt = Math.min(rawDeltaSec, 0.05);
    this.accumulator += clampedDt;

    let snapshotToPublish: MatchStateSnapshot | null = null;
    let lastEvents = noEvents;
    let lastHit: HitFeedback | null = null;
    let lastRes: PointResolution | null = null;

    while (this.accumulator >= CourtSpec.FIXED_TIMESTEP_SEC) {
      if (this.isHost) {
        const res = this.stepHost(CourtSpec.FIXED_TIMESTEP_SEC);
        lastEvents = res.stepEvents;
        if (res.hitFeedback) lastHit = res.hitFeedback;
        if (res.pointResolution) lastRes = res.pointResolution;

        this.publishAccumulator += CourtSpec.FIXED_TIMESTEP_SEC;
        if (this.publishAccumulator >= OnlineMatch1v1.PUBLISH_INTERVAL_SEC) {
          this.publishAccumulator -= OnlineMatch1v1.PUBLISH_INTERVAL_SEC;
          snapshotToPublish = this.buildSnapshot();
        }
      } else {
        this.stepGuest(CourtSpec.FIXED_TIMESTEP_SEC);
      }
      this.accumulator -= CourtSpec.FIXED_TIMESTEP_SEC;
    }

    return {
      snapshotToPublish,
      stepEvents: lastEvents,
      hitFeedback: lastHit,
      pointResolution: lastRes,
    };
  }

  buildSnapshot(): MatchStateSnapshot {
    return {
      tick: this.tickCounter,
      ballX: this.ball.x,
      ballY: this.ball.y,
      ballZ: this.ball.z,
      ballVx: this.ball.vx,
      ballVy: this.ball.vy,
      ballVz: this.ball.vz,
      ballActive: this.ball.active,
      hostX: this.localPlayer.x,
      hostY: this.localPlayer.y,
      guestX: this.remotePlayer.x,
      guestY: this.remotePlayer.y,
      hostSwingTimer: this.localPlayer.swingTimer,
      guestSwingTimer: this.remotePlayer.swingTimer,
      scoreHost: this.rules.scoreLower,
      scoreGuest: this.rules.scoreUpper,
      servingSide: this.rules.servingSide === 'LOWER' ? 'HOST' : 'GUEST',
      rallyShotCount: this.rules.rallyShotCount,
      rallyHits: this.rallyHits,
      lastEvent: this.lastCallout,
      matchWinner:
        this.rules.matchWinner === 'LOWER'
          ? 'HOST'
          : this.rules.matchWinner === 'UPPER'
            ? 'GUEST'
            : null,
      publishedAtMs: Date.now(),
    };
  }

  private stepGuest(dt: number) {
    this.moveTowardTarget(this.localPlayer, OnlineMatch1v1.HOST_PLAYER_SPEED_FT_S, dt);
    const lerpAlpha = Math.min(1, dt * 18);
    this.remotePlayer.x += (this.interpRemoteX - this.remotePlayer.x) * lerpAlpha;
    this.remotePlayer.y += (this.interpRemoteY - this.remotePlayer.y) * lerpAlpha;
    this.ball.x += (this.interpBallX - this.ball.x) * lerpAlpha;
    this.ball.y += (this.interpBallY - this.ball.y) * lerpAlpha;
    this.ball.z += (this.interpBallZ - this.ball.z) * lerpAlpha;

    if (this.localPlayer.swingTimer > 0) this.localPlayer.swingTimer = Math.max(0, this.localPlayer.swingTimer - dt);
    if (this.remotePlayer.swingTimer > 0) this.remotePlayer.swingTimer = Math.max(0, this.remotePlayer.swingTimer - dt);
  }

  private stepHost(dt: number): {
    stepEvents: StepEvents;
    hitFeedback: HitFeedback | null;
    pointResolution: PointResolution | null;
  } {
    this.tickCounter += 1;
    this.moveTowardTarget(this.localPlayer, OnlineMatch1v1.HOST_PLAYER_SPEED_FT_S, dt);
    this.moveTowardTarget(this.remotePlayer, OnlineMatch1v1.HOST_PLAYER_SPEED_FT_S, dt);
    if (this.localPlayer.swingTimer > 0) this.localPlayer.swingTimer = Math.max(0, this.localPlayer.swingTimer - dt);
    if (this.remotePlayer.swingTimer > 0) this.remotePlayer.swingTimer = Math.max(0, this.remotePlayer.swingTimer - dt);

    const noEvents: StepEvents = {
      bounced: false,
      bounceZSpeed: 0,
      bounceX: this.ball.x,
      bounceY: this.ball.y,
      netOutcome: 'NONE',
    };

    if (this.rallyPhase === 'MATCH_OVER') {
      return { stepEvents: noEvents, hitFeedback: null, pointResolution: null };
    }

    if (this.rallyPhase === 'WAITING_SERVE') {
      if (this.rules.servingSide === 'LOWER') {
        this.ball.x = this.localPlayer.x - 1.0;
        this.ball.y = this.localPlayer.y + 1.3;
        this.ball.z = 2.4;
      } else {
        this.ball.x = this.remotePlayer.x + 1.0;
        this.ball.y = this.remotePlayer.y - 1.3;
        this.ball.z = 2.4;
        if (this.pendingGuestSwipe) {
          const swipe = this.pendingGuestSwipe;
          this.pendingGuestSwipe = null;
          const served = this.launchHostAuthoritativeServe('UPPER', swipe);
          return { stepEvents: noEvents, hitFeedback: served.hit, pointResolution: served.fault };
        }
      }
      return { stepEvents: noEvents, hitFeedback: null, pointResolution: null };
    }

    if (this.rallyPhase === 'POINT_PAUSE') {
      this.pauseTimerSec -= dt;
      if (this.pauseTimerSec <= 0) {
        this.setupPointForServe();
      }
      return { stepEvents: noEvents, hitFeedback: null, pointResolution: null };
    }

    let guestHit: HitFeedback | null = null;
    if (this.pendingGuestSwipe) {
      const swipe = this.pendingGuestSwipe;
      this.pendingGuestSwipe = null;
      const res = this.handleGuestSwingOnHost(swipe);
      guestHit = res.hit;
      if (res.fault) {
        return { stepEvents: noEvents, hitFeedback: null, pointResolution: res.fault };
      }
    }

    const stepEvents = stepBallPhysics(this.ball, dt);
    this.lastPointBounce = stepEvents.bounced;
    void this.lastPointBounce;

    if (stepEvents.netOutcome === 'HIT_NET') {
      const fault = this.applyFault(this.ball.lastHitterSide ?? 'LOWER', 'INTO_NET');
      return { stepEvents, hitFeedback: guestHit, pointResolution: fault };
    }

    if (stepEvents.bounced) {
      if (this.ball.bouncesOnCurrentSide === 1) {
        const bounceFault = this.rules.validateFirstBounce(
          stepEvents.bounceX,
          stepEvents.bounceY,
          this.ball.lastHitterSide,
        );
        if (bounceFault) {
          const fault = this.applyFault(this.ball.lastHitterSide ?? 'LOWER', bounceFault);
          return { stepEvents, hitFeedback: guestHit, pointResolution: fault };
        }
      }
      if (this.ball.bouncesOnCurrentSide >= 2) {
        const receiver: CourtSide = this.ball.lastHitterSide === 'LOWER' ? 'UPPER' : 'LOWER';
        const fault = this.applyFault(receiver, 'DOUBLE_BOUNCE');
        return { stepEvents, hitFeedback: guestHit, pointResolution: fault };
      }
    }

    if (
      this.ball.x < -2 ||
      this.ball.x > CourtSpec.WORLD_WIDTH_FT + 2 ||
      this.ball.y < -3 ||
      this.ball.y > CourtSpec.WORLD_HEIGHT_FT + 3
    ) {
      if (this.ball.crossedNetSinceHit && this.ball.bouncesOnCurrentSide >= 1) {
        const receiver: CourtSide = this.ball.lastHitterSide === 'LOWER' ? 'UPPER' : 'LOWER';
        const fault = this.applyFault(receiver, 'DOUBLE_BOUNCE');
        return { stepEvents, hitFeedback: guestHit, pointResolution: fault };
      }
      const faulting = this.ball.lastHitterSide ?? 'LOWER';
      const reason: FaultReason =
        this.rules.rallyShotCount === 1 ? 'SERVE_OUT_OF_BOX' : 'OUT_OF_BOUNDS';
      const fault = this.applyFault(faulting, reason);
      return { stepEvents, hitFeedback: guestHit, pointResolution: fault };
    }

    return { stepEvents, hitFeedback: guestHit, pointResolution: null };
  }

  private handleHostSwipe(swipe: SwipeVector): {
    hit: HitFeedback | null;
    fault: PointResolution | null;
  } {
    if (this.rallyPhase === 'WAITING_SERVE' && this.rules.servingSide === 'LOWER' && swipe.dyFt > 1.0) {
      return this.launchHostAuthoritativeServe('LOWER', swipe);
    }
    if (this.rallyPhase !== 'RALLY_LIVE' || !this.ball.active || this.ball.lastHitterSide === 'LOWER') {
      return { hit: null, fault: null };
    }
    const dist = Math.hypot(this.ball.x - this.localPlayer.x, this.ball.y - this.localPlayer.y);
    if (dist > OnlineMatch1v1.LOCAL_HIT_REACH_FT || this.ball.z > OnlineMatch1v1.MAX_HIT_HEIGHT_FT) {
      return { hit: null, fault: null };
    }

    const faultReason = this.rules.validatePlayerHit(
      'LOWER',
      this.localPlayer.x,
      this.localPlayer.y,
      this.ball.bouncesOnCurrentSide,
    );
    if (faultReason) {
      return { hit: null, fault: this.applyFault('LOWER', faultReason) };
    }

    const shotType = classifySwipe(swipe);
    const { vx, vy, vz } = computeShotVelocity(
      this.ball.x,
      this.ball.y,
      swipe,
      shotType,
      'UPPER',
      0.9,
      (Math.random() * 2 - 1) * 0.4,
      this.ball.z,
    );

    this.ball.vx = vx;
    this.ball.vy = vy;
    this.ball.vz = vz;
    this.ball.lastHitterSide = 'LOWER';
    this.ball.crossedNetSinceHit = false;
    this.ball.bouncesOnCurrentSide = 0;
    this.localPlayer.swingTimer = 0.22;
    this.localPlayer.lastSwingType = shotType;
    this.bestRally = Math.max(this.bestRally, this.rules.rallyShotCount);
    this.rallyHits = this.rules.rallyShotCount >= 3 ? this.rules.rallyShotCount : 0;
    this.lastShotLabel = `Host ${shotType}`;
    this.lastCallout = `Host ${shotType}`;

    return {
      hit: {
        hitterSide: 'LOWER',
        shotType,
        timingAccuracy: 0.9,
        hitX: this.ball.x,
        hitY: this.ball.y,
      },
      fault: null,
    };
  }

  private handleGuestSwingOnHost(swipe: SwipeVector): {
    hit: HitFeedback | null;
    fault: PointResolution | null;
  } {
    if (this.rallyPhase !== 'RALLY_LIVE' || !this.ball.active || this.ball.lastHitterSide === 'UPPER') {
      return { hit: null, fault: null };
    }
    const dist = Math.hypot(this.ball.x - this.remotePlayer.x, this.ball.y - this.remotePlayer.y);
    if (
      dist > OnlineMatch1v1.REMOTE_GENEROUS_REACH_FT ||
      this.ball.z > OnlineMatch1v1.MAX_HIT_HEIGHT_FT + 1.0
    ) {
      return { hit: null, fault: null };
    }

    const faultReason = this.rules.validatePlayerHit(
      'UPPER',
      this.remotePlayer.x,
      this.remotePlayer.y,
      this.ball.bouncesOnCurrentSide,
    );
    if (faultReason) {
      return { hit: null, fault: this.applyFault('UPPER', faultReason) };
    }

    const shotType = classifySwipe(swipe);
    const { vx, vy, vz } = computeShotVelocity(
      this.ball.x,
      this.ball.y,
      swipe,
      shotType,
      'LOWER',
      0.9,
      (Math.random() * 2 - 1) * 0.4,
      this.ball.z,
    );

    this.ball.vx = vx;
    this.ball.vy = vy;
    this.ball.vz = vz;
    this.ball.lastHitterSide = 'UPPER';
    this.ball.crossedNetSinceHit = false;
    this.ball.bouncesOnCurrentSide = 0;
    this.remotePlayer.swingTimer = 0.22;
    this.remotePlayer.lastSwingType = shotType;
    this.bestRally = Math.max(this.bestRally, this.rules.rallyShotCount);
    this.rallyHits = this.rules.rallyShotCount >= 3 ? this.rules.rallyShotCount : 0;
    this.lastCallout = `Guest ${shotType}`;

    return {
      hit: {
        hitterSide: 'UPPER',
        shotType,
        timingAccuracy: 0.9,
        hitX: this.ball.x,
        hitY: this.ball.y,
      },
      fault: null,
    };
  }

  private launchHostAuthoritativeServe(
    serverSide: CourtSide,
    swipe: SwipeVector,
  ): { hit: HitFeedback | null; fault: PointResolution | null } {
    const assign = this.rules.currentServeAssignment();
    const server = serverSide === 'LOWER' ? this.localPlayer : this.remotePlayer;
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

    const tof = 0.96;
    const aimOffset = Math.max(-2.8, Math.min(2.8, swipe.dxFt * 0.45));
    this.ball.vx = (targetX + aimOffset - this.ball.x) / tof;
    this.ball.vy = (targetY - this.ball.y) / tof;
    this.ball.vz = -0.5 * BallPhysicsConstants.GRAVITY_FT_S2 * tof - this.ball.z / tof;
    this.ball.active = true;
    this.ball.lastHitterSide = serverSide;
    this.ball.crossedNetSinceHit = false;
    this.ball.bouncesOnCurrentSide = 0;

    server.swingTimer = 0.24;
    server.lastSwingType = 'SERVE';
    this.rallyPhase = 'RALLY_LIVE';
    this.rallyHits = 0;
    this.bestRally = Math.max(this.bestRally, 1);
    this.lastCallout = 'Serve!';

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

  private applyFault(faultingSide: CourtSide, reason: FaultReason): PointResolution | null {
    if (this.rallyPhase === 'POINT_PAUSE' || this.rallyPhase === 'MATCH_OVER') {
      return null;
    }
    const res = this.rules.resolveFault(faultingSide, reason);
    this.lastFaultReason = reason;
    this.scoreLocal = this.rules.scoreLower;
    this.scoreRemote = this.rules.scoreUpper;
    this.lastCallout = res.calloutText;
    this.rallyPhase = res.matchWinner ? 'MATCH_OVER' : 'POINT_PAUSE';
    this.matchWinnerLocal =
      res.matchWinner === 'LOWER' ? true : res.matchWinner === 'UPPER' ? false : null;
    this.pauseTimerSec = OnlineMatch1v1.POINT_PAUSE_SEC;
    this.ball.active = false;
    return res;
  }

  private moveTowardTarget(entity: PlayerEntity, maxSpeed: number, dt: number) {
    const dx = entity.targetX - entity.x;
    const dy = entity.targetY - entity.y;
    const dist = Math.hypot(dx, dy);
    if (dist < 0.04) {
      entity.x = entity.targetX;
      entity.y = entity.targetY;
      return;
    }
    const step = Math.min(dist, maxSpeed * dt);
    entity.x += (dx / dist) * step;
    entity.y += (dy / dist) * step;
  }
}

export function runOnline1v1Tests(): SpecTestResult[] {
  const results: SpecTestResult[] = [];

  const validCodes = Array.from({ length: 12 }, () => RoomCodeGenerator.generate()).every((c) =>
    RoomCodeGenerator.isValid(c),
  );
  const rejectsBad =
    !RoomCodeGenerator.isValid('PKL-42') &&
    !RoomCodeGenerator.isValid('ABC-123') &&
    RoomCodeGenerator.isValid('pkl-482');
  results.push({
    id: 'room-code-format',
    name: 'Room code generator & PKL-### format validation',
    passed: validCodes && rejectsBad,
    detail: 'Matches PKL-[0-9]{3} & database.rules.json',
  });

  const host = new OnlineMatch1v1(true, 'host-1');
  const guest = new OnlineMatch1v1(false, 'guest-2');
  const snap = host.buildSnapshot();
  guest.applyHostSnapshot(snap);
  for (let i = 0; i < 30; i++) {
    guest.update(CourtSpec.FIXED_TIMESTEP_SEC);
  }
  const expectedX = CourtSpec.WORLD_WIDTH_FT - snap.hostX;
  const expectedY = CourtSpec.WORLD_HEIGHT_FT - snap.hostY;
  const mirrorOk =
    Math.abs(guest.remotePlayer.x - expectedX) < 0.25 &&
    Math.abs(guest.remotePlayer.y - expectedY) < 0.25 &&
    guest.localPlayer.y < CourtSpec.NET_Y &&
    guest.remotePlayer.y > CourtSpec.NET_Y;
  results.push({
    id: 'online-perspective-mirror',
    name: 'Guest local-bottom perspective mirroring & interpolation',
    passed: mirrorOk,
    detail: 'Host (18.5, 4.9) mirrors to Guest upper (9.5, 51.1)',
  });

  const rateHost = new OnlineMatch1v1(true, 'host-rate');
  let emitted = 0;
  for (let i = 0; i < 60; i++) {
    if (rateHost.update(CourtSpec.FIXED_TIMESTEP_SEC).snapshotToPublish) emitted++;
  }
  results.push({
    id: 'online-20hz-publish',
    name: 'Host-authoritative 60 Hz sim emits 20 Hz state snapshots',
    passed: emitted === 20,
    detail: `1.0s sim emitted ${emitted} state snapshots`,
  });

  const dcGuest = new OnlineMatch1v1(false, 'guest-dc');
  dcGuest.onRoomMetaUpdated('HOST_LEFT');
  results.push({
    id: 'online-host-disconnect',
    name: 'Host disconnect ends guest match gracefully',
    passed: dcGuest.hostDisconnected && dcGuest.rallyPhase === 'MATCH_OVER',
    detail: 'RoomStatus.HOST_LEFT -> Match ends with Host disconnected',
  });

  return results;
}
