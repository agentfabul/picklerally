import { CourtSpec, type CourtSide, type SpecTestResult } from './courtSpec';
import {
  RulesEngine,
  type FaultReason,
  type PointResolution,
  type ScoringMode,
} from './rulesEngine';

export type CharacterId =
  | 'RIO'
  | 'LOLA'
  | 'BERT'
  | 'DILL'
  | 'MAYA'
  | 'KAI'
  | 'ZOE'
  | 'GUS'
  | 'NINA'
  | 'DUKE';

export interface CharacterPalette {
  id: CharacterId;
  name: string;
  tagline: string;
  description: string;
  skin: string;
  shirt: string;
  headwear: string;
  paddle: string;
  hair: string;
  hasGlasses?: boolean;
  hasBeard?: boolean;
  isPickleMascot?: boolean;
  isUnlockable?: boolean;
  /** Wider torso in portraits. */
  bigBuild?: boolean;
  /** Tank-top straps in portraits. */
  tankTop?: boolean;
  bodyRadiusFt: number;
}

export const CHARACTER_PALETTES: Record<CharacterId, CharacterPalette> = {
  RIO: {
    id: 'RIO',
    name: 'Rio',
    tagline: 'Speedy rookie',
    description: 'Orange headband, blue shirt',
    skin: '#F3C69F',
    shirt: '#2563EB',
    headwear: '#F97316',
    paddle: '#84CC16',
    hair: '#4B2E1E',
    bodyRadiusFt: 1.12,
  },
  LOLA: {
    id: 'LOLA',
    name: 'Lola',
    tagline: 'Dink master',
    description: 'Pink visor, gray hair, glasses',
    skin: '#F0C2A2',
    shirt: '#7C3AED',
    headwear: '#EC4899',
    paddle: '#38BDF8',
    hair: '#94A3B8',
    hasGlasses: true,
    bodyRadiusFt: 1.12,
  },
  BERT: {
    id: 'BERT',
    name: 'Bert',
    tagline: 'Power driver',
    description: 'Big build, beard, green tank top',
    skin: '#E9B68E',
    shirt: '#16A34A',
    headwear: '#78350F',
    paddle: '#F59E0B',
    hair: '#78350F',
    bigBuild: true,
    tankTop: true,
    hasBeard: true,
    bodyRadiusFt: 1.28,
  },
  DILL: {
    id: 'DILL',
    name: 'Dill',
    tagline: 'Pickle mascot',
    description: 'Unlockable pickle mascot, red headband',
    skin: '#65A30D',
    shirt: '#4D7C0F',
    headwear: '#EF4444',
    paddle: '#FACC15',
    hair: '#3F6212',
    isPickleMascot: true,
    isUnlockable: true,
    bodyRadiusFt: 1.12,
  },
  MAYA: {
    id: 'MAYA',
    name: 'Maya',
    tagline: 'Spin specialist',
    description: 'Teal visor, purple shirt, black ponytail',
    skin: '#C68642',
    shirt: '#9333EA',
    headwear: '#14B8A6',
    paddle: '#FACC15',
    hair: '#111827',
    bodyRadiusFt: 1.12,
  },
  KAI: {
    id: 'KAI',
    name: 'Kai',
    tagline: 'Kitchen king',
    description: 'Yellow headband, red shirt, glasses',
    skin: '#8D5524',
    shirt: '#DC2626',
    headwear: '#FACC15',
    paddle: '#22D3EE',
    hair: '#1F2937',
    hasGlasses: true,
    bodyRadiusFt: 1.12,
  },
  ZOE: {
    id: 'ZOE',
    name: 'Zoe',
    tagline: 'Lob queen',
    description: 'White headband, pink shirt, red hair',
    skin: '#FFDBAC',
    shirt: '#EC4899',
    headwear: '#F8FAFC',
    paddle: '#A3E635',
    hair: '#B91C1C',
    bodyRadiusFt: 1.12,
  },
  GUS: {
    id: 'GUS',
    name: 'Gus',
    tagline: 'Veteran grinder',
    description: 'Gray beard, navy polo, green headband',
    skin: '#E0AC69',
    shirt: '#1E3A8A',
    headwear: '#15803D',
    paddle: '#F97316',
    hair: '#9CA3AF',
    hasBeard: true,
    bodyRadiusFt: 1.12,
  },
  NINA: {
    id: 'NINA',
    name: 'Nina',
    tagline: 'Net rusher',
    description: 'Black headband, orange shirt, glasses',
    skin: '#F1C27D',
    shirt: '#EA580C',
    headwear: '#0F172A',
    paddle: '#EC4899',
    hair: '#3B2314',
    hasGlasses: true,
    bodyRadiusFt: 1.12,
  },
  DUKE: {
    id: 'DUKE',
    name: 'Duke',
    tagline: 'Hard hitter',
    description: 'Big build, purple tank top, gold headband',
    skin: '#5C3A21',
    shirt: '#7C3AED',
    headwear: '#EAB308',
    paddle: '#F43F5E',
    hair: '#0B0B0B',
    bigBuild: true,
    tankTop: true,
    bodyRadiusFt: 1.28,
  },
};

export type ShotType = 'DINK' | 'DRIVE' | 'LOB' | 'SERVE';
export type NetCrossOutcome = 'NONE' | 'CLEARED' | 'HIT_NET';
export type RallyPhase = 'WAITING_SERVE' | 'RALLY_LIVE' | 'POINT_PAUSE' | 'MATCH_OVER';

export interface BallState {
  x: number;
  y: number;
  z: number;
  vx: number;
  vy: number;
  vz: number;
  active: boolean;
  bouncesOnCurrentSide: number;
  lastHitterSide: CourtSide | null;
  crossedNetSinceHit: boolean;
}

export interface StepEvents {
  bounced: boolean;
  bounceZSpeed: number;
  bounceX: number;
  bounceY: number;
  netOutcome: NetCrossOutcome;
}

export interface SwipeVector {
  dxFt: number;
  dyFt: number;
  durationSec: number;
}

export const BallPhysicsConstants = {
  GRAVITY_FT_S2: -34.0,
  BOUNCE_RESTITUTION: 0.76,
  HORIZONTAL_AIR_DRAG: 0.14,
  BOUNCE_HORIZONTAL_FRICTION: 0.92,
  BALL_RADIUS_FT: 0.16,
};

export function stepBallPhysics(
  ball: BallState,
  dt = CourtSpec.FIXED_TIMESTEP_SEC,
): StepEvents {
  if (!ball.active) {
    return {
      bounced: false,
      bounceZSpeed: 0,
      bounceX: ball.x,
      bounceY: ball.y,
      netOutcome: 'NONE',
    };
  }

  const prevX = ball.x;
  const prevY = ball.y;
  const prevZ = ball.z;

  const dragFactor = Math.max(0, 1 - BallPhysicsConstants.HORIZONTAL_AIR_DRAG * dt);
  ball.vx *= dragFactor;
  ball.vy *= dragFactor;

  ball.vz += BallPhysicsConstants.GRAVITY_FT_S2 * dt;
  ball.x += ball.vx * dt;
  ball.y += ball.vy * dt;
  ball.z += ball.vz * dt;

  let netOutcome: NetCrossOutcome = 'NONE';
  const crossedNet =
    (prevY < CourtSpec.NET_Y && ball.y >= CourtSpec.NET_Y) ||
    (prevY > CourtSpec.NET_Y && ball.y <= CourtSpec.NET_Y);

  if (crossedNet) {
    const dy = ball.y - prevY;
    const t = Math.abs(dy) > 1e-5 ? Math.min(1, Math.max(0, (CourtSpec.NET_Y - prevY) / dy)) : 0.5;
    const crossX = prevX + (ball.x - prevX) * t;
    const crossZ = prevZ + (ball.z - prevZ) * t;
    const requiredZ =
      CourtSpec.netHeightAt(crossX) + BallPhysicsConstants.BALL_RADIUS_FT * 0.6;

    const withinPosts =
      crossX >= CourtSpec.COURT_LEFT_X - CourtSpec.NET_POST_OFFSET_FT &&
      crossX <= CourtSpec.COURT_RIGHT_X + CourtSpec.NET_POST_OFFSET_FT;

    if (withinPosts && crossZ < requiredZ) {
      netOutcome = 'HIT_NET';
      ball.x = crossX;
      ball.y = CourtSpec.NET_Y - Math.sign(ball.vy || 1) * 0.22;
      ball.z = Math.max(0.2, crossZ);
      ball.vx *= 0.25;
      ball.vy = -ball.vy * 0.22;
      ball.vz = -1.5;
    } else {
      netOutcome = 'CLEARED';
      ball.crossedNetSinceHit = true;
      ball.bouncesOnCurrentSide = 0;
    }
  }

  let bounced = false;
  let bounceZSpeed = 0;
  let bounceX = ball.x;
  let bounceY = ball.y;
  const rawZ = ball.z;
  if (rawZ <= 0) {
    const denom = prevZ - rawZ;
    const tBounce = prevZ > 0 && denom > 1e-5 ? Math.min(1, Math.max(0, prevZ / denom)) : 1;
    bounceX = prevX + (ball.x - prevX) * tBounce;
    bounceY = prevY + (ball.y - prevY) * tBounce;

    bounceZSpeed = Math.abs(ball.vz);
    ball.z = 0;
    if (bounceZSpeed > 1.2) {
      ball.vz = bounceZSpeed * BallPhysicsConstants.BOUNCE_RESTITUTION;
      ball.vx *= BallPhysicsConstants.BOUNCE_HORIZONTAL_FRICTION;
      ball.vy *= BallPhysicsConstants.BOUNCE_HORIZONTAL_FRICTION;
    } else {
      ball.vz = 0;
      ball.vx *= 0.85;
      ball.vy *= 0.85;
    }
    ball.bouncesOnCurrentSide += 1;
    bounced = true;
  }

  return {
    bounced,
    bounceZSpeed,
    bounceX,
    bounceY,
    netOutcome,
  };
}

export function classifySwipe(swipe: SwipeVector): ShotType {
  const dist = Math.hypot(swipe.dxFt, swipe.dyFt);
  const speed = dist / Math.max(swipe.durationSec, 0.04);
  const forwardRatio = dist > 0.01 ? Math.abs(swipe.dyFt) / dist : 0;

  if (swipe.dyFt > 2.2 && forwardRatio > 0.72 && speed >= 10 && speed <= 27 && dist < 5.2) {
    return 'LOB';
  }
  if (dist < 2.6 || speed < 11.5) {
    return 'DINK';
  }
  return 'DRIVE';
}

export type BotDifficulty = 'EASY' | 'NORMAL' | 'HARD' | 'PRO';

export function botErrorChance(difficulty: BotDifficulty = 'NORMAL'): number {
  if (difficulty === 'EASY') return 0.24;
  if (difficulty === 'HARD') return 0.05;
  if (difficulty === 'PRO') return 0.02;
  return 0.12;
}

/** How each bot level plays: movement speed, reach, how often it aims away from you,
 *  and how close to the sidelines it dares to aim (smaller = riskier, harder to reach). */
export interface BotProfile {
  label: string;
  speedFtS: number;
  reachScale: number;
  aimAwayChance: number;
  sidelineMarginFt: number;
}

export const BOT_PROFILES: Record<BotDifficulty, BotProfile> = {
  EASY: { label: 'Easy', speedFtS: 10.5, reachScale: 0.8, aimAwayChance: 0.35, sidelineMarginFt: 3.2 },
  NORMAL: { label: 'Normal', speedFtS: 14.2, reachScale: 0.95, aimAwayChance: 1, sidelineMarginFt: 2.0 },
  HARD: { label: 'Hard', speedFtS: 16.5, reachScale: 1.0, aimAwayChance: 1, sidelineMarginFt: 1.6 },
  PRO: { label: 'Pro', speedFtS: 18.5, reachScale: 1.05, aimAwayChance: 1, sidelineMarginFt: 1.2 },
};

export function horizontalDragIntegral(flightTimeSec: number): number {
  const d = BallPhysicsConstants.HORIZONTAL_AIR_DRAG;
  if (d <= 1e-5) return flightTimeSec;
  return (1 - Math.exp(-d * flightTimeSec)) / d;
}

function timeToCrossNet(fromY: number, targetY: number, flightTimeSec: number): { tNet: number; fracNet: number } {
  const dyTotal = targetY - fromY;
  const dyNet = CourtSpec.NET_Y - fromY;
  if (Math.abs(dyTotal) < 1e-4 || dyTotal * dyNet <= 0 || Math.abs(dyNet) >= Math.abs(dyTotal)) {
    return { tNet: flightTimeSec * 0.5, fracNet: 0.5 };
  }
  const fracNet = Math.max(0.05, Math.min(0.95, dyNet / dyTotal));
  const d = BallPhysicsConstants.HORIZONTAL_AIR_DRAG;
  if (d <= 1e-5) {
    return { tNet: flightTimeSec * fracNet, fracNet };
  }
  const expNet = Math.max(0.02, 1 - fracNet * (1 - Math.exp(-d * flightTimeSec)));
  return { tNet: -Math.log(expNet) / d, fracNet };
}

/**
 * Solve launch velocity (vx, vy, vz) so the ball travels from (fromX, fromY, fromZ)
 * to land at (targetX, targetY, z=0) while guaranteeing net clearance when minNetClearanceFt >= 0.
 */
export function computeTargetShotVelocity(
  fromX: number,
  fromY: number,
  fromZ: number,
  targetX: number,
  targetY: number,
  shotType: ShotType,
  minNetClearanceFt = shotType === 'LOB' ? 1.8 : shotType === 'DINK' ? 0.65 : 0.5,
): { vx: number; vy: number; vz: number } {
  const z0 = Math.max(0.25, fromZ);
  const dist = Math.hypot(targetX - fromX, targetY - fromY);

  let flightTime =
    shotType === 'DINK'
      ? Math.min(0.84, Math.max(0.62, dist / 24))
      : shotType === 'LOB'
        ? Math.min(1.58, Math.max(1.24, dist / 27))
        : shotType === 'SERVE'
          ? 0.94
          : Math.min(0.84, Math.max(0.58, dist / 46));

  let vz = (-0.5 * BallPhysicsConstants.GRAVITY_FT_S2 * flightTime * flightTime - z0) / flightTime;

  for (let i = 0; i < 36; i++) {
    const { tNet, fracNet } = timeToCrossNet(fromY, targetY, flightTime);
    const netX = fromX + (targetX - fromX) * fracNet;
    const baseRequiredNetZ =
      CourtSpec.netHeightAt(netX) + BallPhysicsConstants.BALL_RADIUS_FT * 0.6;
    const zAtNet =
      z0 + vz * tNet + 0.5 * BallPhysicsConstants.GRAVITY_FT_S2 * tNet * tNet;

    if (minNetClearanceFt < 0) {
      // Intentional unforced net error: flatten arc slightly below required net height
      vz = (baseRequiredNetZ - 0.35 - z0 - 0.5 * BallPhysicsConstants.GRAVITY_FT_S2 * tNet * tNet) / Math.max(0.15, tNet);
      break;
    }

    if (zAtNet >= baseRequiredNetZ + minNetClearanceFt || flightTime >= 2.2) {
      break;
    }
    flightTime += 0.04;
    vz = (-0.5 * BallPhysicsConstants.GRAVITY_FT_S2 * flightTime * flightTime - z0) / flightTime;
  }

  const invIntegral = 1 / horizontalDragIntegral(flightTime);
  const vx = (targetX - fromX) * invIntegral;
  const vy = (targetY - fromY) * invIntegral;

  return { vx, vy, vz };
}

export interface BotShotParams {
  fromX: number;
  fromY: number;
  fromZ: number;
  hitterY: number;
  targetSide: CourtSide;
  opponentXs: number[];
  difficulty?: BotDifficulty;
  /** 0-1: chance to aim away from the opponent (otherwise aims near them). */
  aimAwayChance?: number;
  /** Safety margin from the sidelines when picking a target. */
  sidelineMarginFt?: number;
  randFn?: () => number;
}

export function computeBotReturnShot({
  fromX,
  fromY,
  fromZ,
  hitterY,
  targetSide,
  opponentXs,
  difficulty = 'NORMAL',
  aimAwayChance = 1,
  sidelineMarginFt = 2.0,
  randFn = Math.random,
}: BotShotParams): { shotType: ShotType; vx: number; vy: number; vz: number; targetX: number; targetY: number } {
  // Keep ~10% margin from sidelines (2.0 ft on 20 ft width) and baseline (2.2 ft on 22 ft half)
  const minX = CourtSpec.COURT_LEFT_X + sidelineMarginFt;
  const maxX = CourtSpec.COURT_RIGHT_X - sidelineMarginFt;
  const minY = targetSide === 'LOWER' ? CourtSpec.COURT_BOTTOM_Y + 2.2 : CourtSpec.NET_Y + 2.6;
  const maxY = targetSide === 'LOWER' ? CourtSpec.NET_Y - 2.6 : CourtSpec.COURT_TOP_Y - 2.2;

  const roll = randFn();
  const nearKitchen = Math.abs(hitterY - CourtSpec.NET_Y) < 9.5;
  const shotType: ShotType =
    nearKitchen && roll < 0.38 ? 'DINK' : roll < 0.85 ? 'DRIVE' : 'LOB';

  // Aim away from opponents while remaining strictly inside safe court margins
  const oppX =
    opponentXs.length === 0
      ? CourtSpec.COURT_CENTER_X
      : opponentXs[Math.floor(randFn() * opponentXs.length)];
  const aimAway = aimAwayChance >= 1 || randFn() < aimAwayChance;
  const preferredX = !aimAway
    ? oppX + (randFn() - 0.5) * 3.0 // easy ball near the opponent
    : oppX >= CourtSpec.COURT_CENTER_X
      ? CourtSpec.COURT_CENTER_X - 3.6 - randFn() * 3.0
      : CourtSpec.COURT_CENTER_X + 3.6 + randFn() * 3.0;
  let targetX = Math.max(minX, Math.min(maxX, preferredX));

  let targetY: number;
  if (shotType === 'DINK') {
    const depth = 3.2 + randFn() * 2.6;
    targetY = targetSide === 'LOWER' ? CourtSpec.NET_Y - depth : CourtSpec.NET_Y + depth;
  } else if (shotType === 'LOB') {
    const marginFromBase = 2.6 + randFn() * 2.8;
    targetY =
      targetSide === 'LOWER'
        ? CourtSpec.COURT_BOTTOM_Y + marginFromBase
        : CourtSpec.COURT_TOP_Y - marginFromBase;
  } else {
    const marginFromBase = 3.0 + randFn() * 6.5;
    targetY =
      targetSide === 'LOWER'
        ? CourtSpec.COURT_BOTTOM_Y + marginFromBase
        : CourtSpec.COURT_TOP_Y - marginFromBase;
  }
  targetY = Math.max(minY, Math.min(maxY, targetY));

  let minClearance = shotType === 'LOB' ? 1.8 : shotType === 'DINK' ? 0.65 : 0.5;

  // Apply realistic difficulty-scaled unforced error chance (~12% on NORMAL)
  if (randFn() < botErrorChance(difficulty)) {
    const errRoll = randFn();
    if (errRoll < 0.45) {
      minClearance = -0.35; // Into net
    } else if (errRoll < 0.75) {
      targetY =
        targetSide === 'LOWER'
          ? CourtSpec.COURT_BOTTOM_Y - (0.8 + randFn() * 1.4)
          : CourtSpec.COURT_TOP_Y + (0.8 + randFn() * 1.4); // Long past baseline
    } else {
      targetX =
        targetX < CourtSpec.COURT_CENTER_X
          ? CourtSpec.COURT_LEFT_X - (0.8 + randFn() * 1.2)
          : CourtSpec.COURT_RIGHT_X + (0.8 + randFn() * 1.2); // Wide outside sideline
    }
  }

  const vel = computeTargetShotVelocity(
    fromX,
    fromY,
    fromZ,
    targetX,
    targetY,
    shotType,
    minClearance,
  );
  return { shotType, ...vel, targetX, targetY };
}

export function computeShotVelocity(
  fromX: number,
  fromY: number,
  swipe: SwipeVector,
  shotType: ShotType,
  targetSide: CourtSide,
  timingAccuracy: number,
  scatterSeed = 0,
  fromZ = 2.0,
): { vx: number; vy: number; vz: number } {
  const rawDx = swipe.dxFt;
  const rawDy = Math.max(0.75, Math.abs(swipe.dyFt));
  const aimRatio = Math.max(-0.9, Math.min(0.9, rawDx / rawDy));

  const errorScale = 1 - Math.max(0, Math.min(1, timingAccuracy));
  const scatterX = scatterSeed * 2.2 * errorScale;
  const scatterDepth = scatterSeed * 1.8 * errorScale;

  let targetY: number;
  if (shotType === 'DINK') {
    targetY =
      targetSide === 'UPPER'
        ? CourtSpec.NET_Y + 4.4 + scatterDepth * 0.4
        : CourtSpec.NET_Y - 4.4 - scatterDepth * 0.4;
  } else if (shotType === 'DRIVE') {
    targetY =
      targetSide === 'UPPER'
        ? CourtSpec.COURT_TOP_Y - 4.2 + scatterDepth
        : CourtSpec.COURT_BOTTOM_Y + 4.2 - scatterDepth;
  } else if (shotType === 'LOB') {
    targetY =
      targetSide === 'UPPER'
        ? CourtSpec.COURT_TOP_Y - 3.4 + scatterDepth * 0.6
        : CourtSpec.COURT_BOTTOM_Y + 3.4 - scatterDepth * 0.6;
  } else {
    targetY =
      targetSide === 'UPPER' ? CourtSpec.UPPER_NVZ_Y + 8.5 : CourtSpec.LOWER_NVZ_Y - 8.5;
  }

  const travelY = Math.max(4, Math.abs(targetY - fromY));
  const rawTargetX = Math.max(
    CourtSpec.COURT_LEFT_X + 1.1,
    Math.min(CourtSpec.COURT_RIGHT_X - 1.1, fromX + aimRatio * travelY * 0.72 + scatterX),
  );

  return computeTargetShotVelocity(fromX, fromY, fromZ, rawTargetX, targetY, shotType);
}

export function spriteScaleForHeight(z: number): number {
  return 1 + Math.min(z, 18) * 0.085;
}

export interface PlayerEntity {
  side: CourtSide;
  characterId: CharacterId;
  x: number;
  y: number;
  targetX: number;
  targetY: number;
  swingTimer: number;
  lastSwingType: ShotType;
}

export interface HitFeedback {
  hitterSide: CourtSide;
  shotType: ShotType;
  timingAccuracy: number;
  hitX: number;
  hitY: number;
}

export interface SimTickOutput {
  stepEvents: StepEvents;
  hitFeedback: HitFeedback | null;
  pointResolution: PointResolution | null;
}

/** Match format chosen on the title screen (solo matches). Games are won by 2. */
export const matchFormat: { pointsToWin: 11 | 15 | 21; bestOf: 1 | 3; difficulty: BotDifficulty } = {
  pointsToWin: 11,
  bestOf: 1,
  difficulty: 'NORMAL',
};

function characterName(id: CharacterId): string {
  return CHARACTER_PALETTES[id]?.name ?? id;
}

export class SinglePlayerMatch {
  static readonly PLAYER_SPEED_FT_S = 18.5;
  static readonly BOT_SPEED_FT_S = 14.2;
  static readonly HIT_REACH_FT = 3.8;
  /** The human player's reach (arm + paddle); matches the ring drawn around them. Bots use HIT_REACH_FT. */
  static readonly PLAYER_REACH_FT = 4.3;
  static readonly MAX_HIT_HEIGHT_FT = 7.5; // about an overhead reach
  static readonly POINT_PAUSE_SEC = 1.5;
  static readonly GAME_BREAK_SEC = 3.5;
  static readonly BOT_SERVE_DELAY_SEC = 2.6;

  rules: RulesEngine;
  player: PlayerEntity;
  bot: PlayerEntity;
  ball: BallState;

  rallyPhase: RallyPhase = 'WAITING_SERVE';
  rallyHits = 0;
  bestRally = 0;
  lastCallout = 'Your serve (0 - 0)';
  lastShotLabel = '';
  lastFaultReason: FaultReason | null = null;
  botServeDelaySec = 0;

  private pauseTimerSec = 0;
  private botCooldownSec = 0;
  private accumulator = 0;
  /** Auto-swing when the ball enters the reach ring (on by default). */
  autoSwingEnabled = true
  private pendingResolution: PointResolution | null = null;
  /** Match format (official: games to 11, win by 2; best of 3 in tournaments). */
  pointsToWin = 11;
  bestOf: 1 | 3 = 1;
  difficulty: BotDifficulty = 'NORMAL';
  gamesLower = 0;
  gamesUpper = 0;
  gameNumber = 1;
  gameScores: string[] = [];
  private gameFirstServer: CourtSide = 'LOWER';
  private autoSwingPrevDist = Number.POSITIVE_INFINITY

  constructor(
    playerChar: CharacterId = 'RIO',
    botChar: CharacterId = 'LOLA',
    scoringMode: ScoringMode = 'RALLY',
  ) {
    this.pointsToWin = matchFormat.pointsToWin;
    this.bestOf = matchFormat.bestOf;
    this.difficulty = matchFormat.difficulty;
    this.rules = new RulesEngine(scoringMode, 'SINGLES', this.pointsToWin, 2, 'LOWER');

    this.player = {
      side: 'LOWER',
      characterId: playerChar,
      x: CourtSpec.COURT_CENTER_X + 4.5,
      y: CourtSpec.COURT_BOTTOM_Y - 1.1,
      targetX: CourtSpec.COURT_CENTER_X + 4.5,
      targetY: CourtSpec.COURT_BOTTOM_Y - 1.1,
      swingTimer: 0,
      lastSwingType: 'DRIVE',
    };

    this.bot = {
      side: 'UPPER',
      characterId: botChar,
      x: CourtSpec.COURT_CENTER_X - 4.5,
      y: CourtSpec.COURT_TOP_Y + 1.1,
      targetX: CourtSpec.COURT_CENTER_X - 4.5,
      targetY: CourtSpec.COURT_TOP_Y + 1.1,
      swingTimer: 0,
      lastSwingType: 'DRIVE',
    };

    this.ball = {
      x: this.player.x - 0.6,
      y: this.player.y + 0.9,
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

  resetMatch(scoringMode: ScoringMode = this.rules.scoringMode) {
    this.gamesLower = 0;
    this.gamesUpper = 0;
    this.gameNumber = 1;
    this.gameScores = [];
    this.gameFirstServer = 'LOWER';
    this.pointsToWin = matchFormat.pointsToWin;
    this.bestOf = matchFormat.bestOf;
    this.difficulty = matchFormat.difficulty;
    this.rules = new RulesEngine(scoringMode, 'SINGLES', this.pointsToWin, 2, 'LOWER');
    this.bestRally = 0;
    this.lastShotLabel = '';
    this.lastFaultReason = null;
    this.setupPointForServe();
  }

  setupPointForServe() {
    if (this.rules.isMatchOver) {
      this.rallyPhase = 'MATCH_OVER';
      this.lastCallout = `${characterName(this.rules.matchWinner === 'LOWER' ? this.player.characterId : this.bot.characterId)} Wins!`;
      return;
    }

    const assign = this.rules.currentServeAssignment();
    this.rallyPhase = 'WAITING_SERVE';
    this.rallyHits = 0;
    this.lastFaultReason = null;

    const lowerServerX =
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

    this.player.x = lowerServerX;
    this.player.y = CourtSpec.COURT_BOTTOM_Y - 1.1;
    this.player.targetX = this.player.x;
    this.player.targetY = this.player.y;
    this.player.swingTimer = 0;

    this.bot.x = upperX;
    this.bot.y = CourtSpec.COURT_TOP_Y + 1.1;
    this.bot.targetX = this.bot.x;
    this.bot.targetY = this.bot.y;
    this.bot.swingTimer = 0;

    if (assign.servingSide === 'LOWER') {
      this.ball.x = this.player.x - 1.0;
      this.ball.y = this.player.y + 1.3;
      this.botServeDelaySec = 0;
      this.lastCallout = `Your serve (${this.rules.formattedScoreCall()})`;
    } else {
      this.ball.x = this.bot.x + 1.0;
      this.ball.y = this.bot.y - 1.3;
      this.botServeDelaySec = SinglePlayerMatch.BOT_SERVE_DELAY_SEC;
      this.lastCallout = `Lola serving (${this.rules.formattedScoreCall()})`;
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

  setPlayerMoveTarget(worldX: number, worldY: number) {
    if (this.rallyPhase === 'MATCH_OVER') return;
    const maxY =
      this.rallyPhase === 'WAITING_SERVE' && this.rules.servingSide === 'LOWER'
        ? CourtSpec.COURT_BOTTOM_Y - 0.3
        : CourtSpec.NET_Y - 0.6;
    this.player.targetX = Math.max(1.5, Math.min(CourtSpec.WORLD_WIDTH_FT - 1.5, worldX));
    this.player.targetY = Math.max(1.5, Math.min(maxY, worldY));
  }

  /** Why Dill can or can't hit right now (drives the ring color and hint). */
  playerHitState(): 'NONE' | 'READY' | 'WAIT_BOUNCE' | 'KITCHEN' | 'TOO_HIGH' {
    const ball = this.ball;
    if (this.rallyPhase !== 'RALLY_LIVE' || !ball.active || ball.lastHitterSide === 'LOWER') return 'NONE';
    const dist = Math.hypot(ball.x - this.player.x, ball.y - this.player.y);
    if (dist > SinglePlayerMatch.PLAYER_REACH_FT || ball.y > CourtSpec.NET_Y + 0.8) return 'NONE';
    if (ball.z > SinglePlayerMatch.MAX_HIT_HEIGHT_FT) return 'TOO_HIGH';
    if (ball.bouncesOnCurrentSide === 0) {
      if (this.rules.rallyShotCount === 1 || this.rules.rallyShotCount === 2) return 'WAIT_BOUNCE';
      if (CourtSpec.isInKitchen(this.player.x, this.player.y, 'LOWER')) return 'KITCHEN';
    }
    return 'READY';
  }

  canPlayerReachBall(): boolean {
    if (this.rallyPhase !== 'RALLY_LIVE' || !this.ball.active) return false;
    if (this.ball.lastHitterSide === 'LOWER') return false;
    const dist = Math.hypot(this.ball.x - this.player.x, this.ball.y - this.player.y);
    return (
      dist <= SinglePlayerMatch.PLAYER_REACH_FT &&
      this.ball.z <= SinglePlayerMatch.MAX_HIT_HEIGHT_FT &&
      this.ball.y <= CourtSpec.NET_Y + 0.8
    );
  }

  launchServe(swipe?: SwipeVector): { hit: HitFeedback | null; fault: PointResolution | null } {
    if (this.rallyPhase !== 'WAITING_SERVE') return { hit: null, fault: null };
    const assign = this.rules.currentServeAssignment();
    const server = assign.servingSide === 'LOWER' ? this.player : this.bot;

    const footFault = this.rules.beginServe(server.x, server.y);
    if (footFault) {
      return { hit: null, fault: this.applyFault(assign.servingSide, footFault) };
    }

    const targetX =
      assign.targetBox === 'LEFT' ? CourtSpec.COURT_LEFT_X + 5.0 : CourtSpec.COURT_RIGHT_X - 5.0;
    const targetY =
      assign.targetCourtSide === 'UPPER'
        ? CourtSpec.UPPER_NVZ_Y + 7.8
        : CourtSpec.LOWER_NVZ_Y - 7.8;

    const tof = 0.96;
    const invIntegral = 1 / horizontalDragIntegral(tof);
    const aimOffset = swipe ? Math.max(-2.6, Math.min(2.6, swipe.dxFt * 0.42)) : 0;
    const vx = (targetX + aimOffset - this.ball.x) * invIntegral;
    const vy = (targetY - this.ball.y) * invIntegral;
    const vz = -0.5 * BallPhysicsConstants.GRAVITY_FT_S2 * tof - this.ball.z / tof;

    this.ball.vx = vx;
    this.ball.vy = vy;
    this.ball.vz = vz;
    this.ball.active = true;
    this.ball.lastHitterSide = assign.servingSide;
    this.ball.crossedNetSinceHit = false;
    this.ball.bouncesOnCurrentSide = 0;

    server.swingTimer = 0.24;
    server.lastSwingType = 'SERVE';
    this.rallyPhase = 'RALLY_LIVE';
    this.botServeDelaySec = 0;
    this.rallyHits = 0;
    this.bestRally = Math.max(this.bestRally, 1);
    this.lastCallout = 'Serve!';
    this.lastShotLabel = 'Serve';

    return {
      hit: {
        hitterSide: assign.servingSide,
        shotType: 'SERVE',
        timingAccuracy: 0.95,
        hitX: this.ball.x,
        hitY: this.ball.y,
      },
      fault: null,
    };
  }

  triggerReadyBotServe(): { hit: HitFeedback | null; fault: PointResolution | null } {
    if (this.rallyPhase !== 'WAITING_SERVE' || this.rules.servingSide !== 'UPPER') {
      return { hit: null, fault: null };
    }
    this.botServeDelaySec = 0;
    return this.launchServe();
  }

  attemptPlayerSwipeHit(
    swipe: SwipeVector,
    forcedShotType?: ShotType,
  ): { hit: HitFeedback | null; fault: PointResolution | null } {
    if (
      this.rallyPhase === 'WAITING_SERVE' &&
      this.rules.servingSide === 'LOWER' &&
      (swipe.dyFt > 1.2 || forcedShotType)
    ) {
      return this.launchServe(swipe);
    }
    if (!this.canPlayerReachBall()) return { hit: null, fault: null };

    const ruleFault = this.rules.validatePlayerHit(
      'LOWER',
      this.player.x,
      this.player.y,
      this.ball.bouncesOnCurrentSide,
    );
    if (ruleFault) {
      return { hit: null, fault: this.applyFault('LOWER', ruleFault) };
    }

    const shotType = forcedShotType ?? classifySwipe(swipe);
    const dist = Math.hypot(this.ball.x - this.player.x, this.ball.y - this.player.y);
    const distScore = 1 - Math.min(1, Math.max(0, dist / SinglePlayerMatch.PLAYER_REACH_FT)) * 0.55;
    const heightSweet = 1 - Math.min(1, Math.max(0, Math.abs(this.ball.z - 2.3) / 3.8)) * 0.45;
    const timingAccuracy = Math.max(0.25, Math.min(1, distScore * 0.6 + heightSweet * 0.4));

    const scatterSeed = Math.random() * 2 - 1;
    const { vx, vy, vz } = computeShotVelocity(
      this.ball.x,
      this.ball.y,
      swipe,
      shotType,
      'UPPER',
      timingAccuracy,
      scatterSeed,
      this.ball.z,
    );

    this.ball.vx = vx;
    this.ball.vy = vy;
    this.ball.vz = vz;
    this.ball.lastHitterSide = 'LOWER';
    this.ball.crossedNetSinceHit = false;
    this.ball.bouncesOnCurrentSide = 0;

    this.player.swingTimer = 0.22;
    this.player.lastSwingType = shotType;
    this.bestRally = Math.max(this.bestRally, this.rules.rallyShotCount);
    this.rallyHits = this.rules.rallyShotCount >= 3 ? this.rules.rallyShotCount : 0;
    const label =
      shotType === 'DINK'
        ? 'Dink'
        : shotType === 'DRIVE'
          ? 'Drive'
          : shotType === 'LOB'
            ? 'Lob'
            : 'Serve';
    this.lastShotLabel = `${label} · ${Math.round(timingAccuracy * 100)}%`;
    this.lastCallout =
      shotType === 'DINK'
        ? 'Soft Dink!'
        : shotType === 'DRIVE'
          ? 'Power Drive!'
          : shotType === 'LOB'
            ? 'High Lob!'
            : 'Serve!';

    return {
      hit: {
        hitterSide: 'LOWER',
        shotType,
        timingAccuracy,
        hitX: this.ball.x,
        hitY: this.ball.y,
      },
      fault: null,
    };
  }

  update(rawDeltaSec: number): SimTickOutput[] {
    const clampedDt = Math.min(rawDeltaSec, 0.05);
    this.accumulator += clampedDt;
    const outputs: SimTickOutput[] = [];

    while (this.accumulator >= CourtSpec.FIXED_TIMESTEP_SEC) {
      outputs.push(this.fixedStep(CourtSpec.FIXED_TIMESTEP_SEC));
      this.accumulator -= CourtSpec.FIXED_TIMESTEP_SEC;
    }
    return outputs;
  }

  private fixedStep(dt: number): SimTickOutput {
    this.moveTowardTarget(this.player, SinglePlayerMatch.PLAYER_SPEED_FT_S, dt);
    if (this.player.swingTimer > 0) this.player.swingTimer = Math.max(0, this.player.swingTimer - dt);
    if (this.bot.swingTimer > 0) this.bot.swingTimer = Math.max(0, this.bot.swingTimer - dt);
    if (this.botCooldownSec > 0) this.botCooldownSec = Math.max(0, this.botCooldownSec - dt);

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
        this.ball.x = this.player.x - 1.0;
        this.ball.y = this.player.y + 1.3;
        this.ball.z = 2.4;
      } else {
        this.ball.x = this.bot.x + 1.0;
        this.ball.y = this.bot.y - 1.3;
        this.ball.z = 2.4;
        this.botServeDelaySec -= dt;
        if (this.botServeDelaySec <= 0) {
          const served = this.launchServe();
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

    this.updateBotAI(dt);
    const botHit = this.tryBotHit();
    if (this.pendingResolution) {
      const pending = this.pendingResolution;
      this.pendingResolution = null;
      return { stepEvents: noEvents, hitFeedback: null, pointResolution: pending };
    }
    // Auto-swing: when the ball is inside Dill's reach ring and a hit would be legal,
    // swing automatically with a straight drive. Swipes and keys still work for aimed shots.
    let autoHit: HitFeedback | null = null
    if (this.autoSwingEnabled && this.canPlayerReachBall()) {
      const dist = Math.hypot(this.ball.x - this.player.x, this.ball.y - this.player.y)
      const isVolley = this.ball.bouncesOnCurrentSide === 0
      const wouldFault =
        isVolley &&
        (this.rules.rallyShotCount === 1 ||
          this.rules.rallyShotCount === 2 ||
          CourtSpec.isInKitchen(this.player.x, this.player.y, 'LOWER'))
      // Swing at the sweet spot, or as the ball starts leaving the ring.
      const leaving = dist > this.autoSwingPrevDist
      this.autoSwingPrevDist = dist
      if (!wouldFault && (dist <= SinglePlayerMatch.PLAYER_REACH_FT * 0.7 || leaving)) {
        const res = this.attemptPlayerSwipeHit({ dxFt: 0, dyFt: 6, durationSec: 0.15 }, 'DRIVE')
        this.autoSwingPrevDist = Number.POSITIVE_INFINITY
        if (res.fault) return { stepEvents: noEvents, hitFeedback: null, pointResolution: res.fault }
        autoHit = res.hit
      }
    } else {
      this.autoSwingPrevDist = Number.POSITIVE_INFINITY
    }
    const stepEvents = stepBallPhysics(this.ball, dt);

    if (stepEvents.netOutcome === 'HIT_NET') {
      const faulting = this.ball.lastHitterSide ?? 'LOWER';
      return {
        stepEvents,
        hitFeedback: null,
        pointResolution: this.applyFault(faulting, 'INTO_NET'),
      };
    }

    if (stepEvents.bounced) {
      if (this.ball.bouncesOnCurrentSide === 1) {
        const bounceFault = this.rules.validateFirstBounce(
          stepEvents.bounceX,
          stepEvents.bounceY,
          this.ball.lastHitterSide,
        );
        if (bounceFault) {
          const faulting = this.ball.lastHitterSide ?? 'LOWER';
          return {
            stepEvents,
            hitFeedback: null,
            pointResolution: this.applyFault(faulting, bounceFault),
          };
        }
      }
      if (this.ball.bouncesOnCurrentSide >= 2) {
        const receiver: CourtSide = this.ball.lastHitterSide === 'LOWER' ? 'UPPER' : 'LOWER';
        return {
          stepEvents,
          hitFeedback: null,
          pointResolution: this.applyFault(receiver, 'DOUBLE_BOUNCE'),
        };
      }
    }

    if (
      this.ball.x < -2 ||
      this.ball.x > CourtSpec.WORLD_WIDTH_FT + 2 ||
      this.ball.y < -3 ||
      this.ball.y > CourtSpec.WORLD_HEIGHT_FT + 3
    ) {
      // Judge in/out strictly on the first bounce: if it already bounced in-bounds on the receiver's side,
      // rolling/rebounding past the baseline means the receiver missed a live ball (point/side-out for hitter).
      if (this.ball.crossedNetSinceHit && this.ball.bouncesOnCurrentSide >= 1) {
        const receiver: CourtSide = this.ball.lastHitterSide === 'LOWER' ? 'UPPER' : 'LOWER';
        return {
          stepEvents,
          hitFeedback: null,
          pointResolution: this.applyFault(receiver, 'DOUBLE_BOUNCE'),
        };
      }
      const faulting = this.ball.lastHitterSide ?? 'LOWER';
      const fault: FaultReason =
        this.rules.rallyShotCount === 1 ? 'SERVE_OUT_OF_BOX' : 'OUT_OF_BOUNDS';
      return {
        stepEvents,
        hitFeedback: null,
        pointResolution: this.applyFault(faulting, fault),
      };
    }

    return {
      stepEvents,
      hitFeedback: botHit ?? autoHit,
      pointResolution: null,
    };
  }

  private updateBotAI(dt: number) {
    if (this.ball.lastHitterSide === 'LOWER' && this.ball.vy > 0) {
      const timeToBotY =
        this.ball.vy > 0.5
          ? Math.min(1.4, Math.max(0, (this.bot.y - this.ball.y) / this.ball.vy))
          : 0.4;
      const predX = Math.max(
        CourtSpec.COURT_LEFT_X + 1,
        Math.min(CourtSpec.COURT_RIGHT_X - 1, this.ball.x + this.ball.vx * timeToBotY),
      );
      const minBotY =
        this.rules.rallyShotCount === 1
          ? CourtSpec.COURT_TOP_Y - 1.5
          : CourtSpec.UPPER_NVZ_Y + 0.8;
      const predY = Math.max(
        minBotY,
        Math.min(CourtSpec.COURT_TOP_Y + 2.0, this.ball.y + this.ball.vy * Math.min(timeToBotY, 0.45)),
      );
      this.bot.targetX = predX;
      this.bot.targetY = predY;
    } else {
      this.bot.targetX = CourtSpec.COURT_CENTER_X;
      this.bot.targetY = CourtSpec.UPPER_NVZ_Y + 2.5;
    }
    this.moveTowardTarget(this.bot, BOT_PROFILES[this.difficulty].speedFtS, dt);
  }

  private tryBotHit(): HitFeedback | null {
    if (this.botCooldownSec > 0 || !this.ball.active) return null;
    if (this.ball.lastHitterSide !== 'LOWER' || !this.ball.crossedNetSinceHit) return null;
    if (this.ball.y < CourtSpec.NET_Y + 0.8) return null;

    // Two-bounce rule: the serve AND the return must bounce before they can be hit.
    if (
      (this.rules.rallyShotCount === 1 || this.rules.rallyShotCount === 2) &&
      this.ball.bouncesOnCurrentSide === 0
    ) {
      return null;
    }
    if (this.ball.bouncesOnCurrentSide === 0 && CourtSpec.isInKitchen(this.bot.x, this.bot.y, 'UPPER')) {
      return null;
    }

    const dist = Math.hypot(this.ball.x - this.bot.x, this.ball.y - this.bot.y);
    if (
      dist > SinglePlayerMatch.HIT_REACH_FT * BOT_PROFILES[this.difficulty].reachScale ||
      this.ball.z > SinglePlayerMatch.MAX_HIT_HEIGHT_FT
    ) {
      return null;
    }

    const fault = this.rules.validatePlayerHit(
      'UPPER',
      this.bot.x,
      this.bot.y,
      this.ball.bouncesOnCurrentSide,
    );
    if (fault) {
      this.pendingResolution = this.applyFault('UPPER', fault);
      return null;
    }

    const { shotType, vx, vy, vz } = computeBotReturnShot({
      fromX: this.ball.x,
      fromY: this.ball.y,
      fromZ: this.ball.z,
      hitterY: this.bot.y,
      targetSide: 'LOWER',
      opponentXs: [this.player.x],
      difficulty: this.difficulty,
      aimAwayChance: BOT_PROFILES[this.difficulty].aimAwayChance,
      sidelineMarginFt: BOT_PROFILES[this.difficulty].sidelineMarginFt,
    });

    this.ball.vx = vx;
    this.ball.vy = vy;
    this.ball.vz = vz;
    this.ball.lastHitterSide = 'UPPER';
    this.ball.crossedNetSinceHit = false;
    this.ball.bouncesOnCurrentSide = 0;

    this.bot.swingTimer = 0.24;
    this.bot.lastSwingType = shotType;
    this.botCooldownSec = 0.35;
    this.bestRally = Math.max(this.bestRally, this.rules.rallyShotCount);
    this.rallyHits = this.rules.rallyShotCount >= 3 ? this.rules.rallyShotCount : 0;
    this.lastCallout =
      shotType === 'DINK' ? 'Bot Dink' : shotType === 'LOB' ? 'Bot Lob' : 'Bot Drive';

    return {
      hitterSide: 'UPPER',
      shotType,
      timingAccuracy: 0.88,
      hitX: this.ball.x,
      hitY: this.ball.y,
    };
  }

  private applyFault(faultingSide: CourtSide, reason: FaultReason): PointResolution | null {
    if (this.rallyPhase === 'POINT_PAUSE' || this.rallyPhase === 'MATCH_OVER') {
      return null;
    }
    const resolution = this.rules.resolveFault(faultingSide, reason);
    this.lastFaultReason = reason;
    this.ball.active = false;
    this.pauseTimerSec = SinglePlayerMatch.POINT_PAUSE_SEC;

    // A game was won: in a best-of-3 match, record it and start the next game.
    if (resolution.matchWinner) {
      const gameWinner = resolution.matchWinner;
      if (gameWinner === 'LOWER') this.gamesLower += 1;
      else this.gamesUpper += 1;
      const gameScore = `${this.rules.scoreLower}-${this.rules.scoreUpper}`;
      this.gameScores.push(gameScore);
      const gamesNeeded = Math.ceil(this.bestOf / 2);
      if (this.gamesLower < gamesNeeded && this.gamesUpper < gamesNeeded) {
        const winnerName = characterName(
          gameWinner === 'LOWER' ? this.player.characterId : this.bot.characterId,
        );
        const finishedGame = this.gameNumber;
        this.gameNumber += 1;
        // Players alternate who serves first each game.
        this.gameFirstServer = this.gameFirstServer === 'LOWER' ? 'UPPER' : 'LOWER';
        this.rules = new RulesEngine(
          this.rules.scoringMode,
          'SINGLES',
          this.pointsToWin,
          2,
          this.gameFirstServer,
        );
        this.pauseTimerSec = SinglePlayerMatch.GAME_BREAK_SEC;
        this.rallyPhase = 'POINT_PAUSE';
        this.lastCallout = `Game ${finishedGame} to ${winnerName} ${gameScore} · Games ${this.gamesLower}-${this.gamesUpper}`;
        return { ...resolution, matchWinner: null, calloutText: this.lastCallout };
      }
    }

    this.lastCallout = resolution.calloutText;
    this.rallyPhase = resolution.matchWinner ? 'MATCH_OVER' : 'POINT_PAUSE';
    return resolution;
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

export function runBallPhysicsTests(): SpecTestResult[] {
  const results: SpecTestResult[] = [];

  const a: BallState = {
    x: 14,
    y: 10,
    z: 2.5,
    vx: 2.1,
    vy: 22,
    vz: 14,
    active: true,
    bouncesOnCurrentSide: 0,
    lastHitterSide: 'LOWER',
    crossedNetSinceHit: false,
  };
  const b: BallState = { ...a };
  for (let i = 0; i < 90; i++) {
    stepBallPhysics(a, CourtSpec.FIXED_TIMESTEP_SEC);
    stepBallPhysics(b, CourtSpec.FIXED_TIMESTEP_SEC);
  }
  const detOk =
    Math.abs(a.x - b.x) < 1e-6 &&
    Math.abs(a.y - b.y) < 1e-6 &&
    Math.abs(a.z - b.z) < 1e-6 &&
    Math.abs(a.vz - b.vz) < 1e-6;

  results.push({
    id: 'deterministic-60hz',
    name: '60 Hz fixed-timestep physics determinism',
    passed: detOk,
    detail: '90-step trajectories match to 1e-6 ft',
  });

  const bounceBall: BallState = {
    x: 14,
    y: 12,
    z: 4.0,
    vx: 0,
    vy: 0,
    vz: 0,
    active: true,
    bouncesOnCurrentSide: 0,
    lastHitterSide: null,
    crossedNetSinceHit: false,
  };
  let bounced = false;
  let peakAfter = 0;
  for (let i = 0; i < 120; i++) {
    const ev = stepBallPhysics(bounceBall, CourtSpec.FIXED_TIMESTEP_SEC);
    if (ev.bounced) bounced = true;
    if (bounced && bounceBall.z > peakAfter) peakAfter = bounceBall.z;
  }
  results.push({
    id: 'bounce-restitution',
    name: 'Gravity drop & bounce energy loss (restitution 0.76)',
    passed: bounced && peakAfter < 4.0 && peakAfter > 1.5,
    detail: `Drop 4.00 ft -> rebound peak ${peakAfter.toFixed(2)} ft`,
  });

  const lowBall: BallState = {
    x: 14,
    y: CourtSpec.NET_Y - 0.3,
    z: 1.8,
    vx: 0,
    vy: 25,
    vz: 0,
    active: true,
    bouncesOnCurrentSide: 0,
    lastHitterSide: 'LOWER',
    crossedNetSinceHit: false,
  };
  const highBall: BallState = { ...lowBall, z: 4.2 };
  const lowEv = stepBallPhysics(lowBall, CourtSpec.FIXED_TIMESTEP_SEC);
  const highEv = stepBallPhysics(highBall, CourtSpec.FIXED_TIMESTEP_SEC);
  results.push({
    id: 'net-check',
    name: 'Interpolated net crossing height check',
    passed: lowEv.netOutcome === 'HIT_NET' && highEv.netOutcome === 'CLEARED',
    detail: '1.8 ft blocked by net · 4.2 ft clears 2.83 ft center strap',
  });

  const dink = classifySwipe({ dxFt: 0.6, dyFt: 1.6, durationSec: 0.25 });
  const drive = classifySwipe({ dxFt: 1.8, dyFt: 6.2, durationSec: 0.16 });
  const lob = classifySwipe({ dxFt: 0.2, dyFt: 3.8, durationSec: 0.2 });
  results.push({
    id: 'swipe-classifier',
    name: 'Swipe shot classifier (Dink / Drive / Lob)',
    passed: dink === 'DINK' && drive === 'DRIVE' && lob === 'LOB',
    detail: 'Short gentle -> Dink · Fast long -> Drive · Upward flick -> Lob',
  });

  const scaleOk = spriteScaleForHeight(6) > spriteScaleForHeight(0);
  results.push({
    id: 'sprite-z-scale',
    name: 'Height-based sprite scale & velocity launch',
    passed: scaleOk,
    detail: `z=0 scale ${spriteScaleForHeight(0).toFixed(2)}x -> z=6 scale ${spriteScaleForHeight(6).toFixed(2)}x`,
  });

  // Simulate 200 bot returns from varied positions on NORMAL difficulty and verify >= 80% land in bounds
  let seed = 1337;
  const seededRand = () => {
    seed = (seed * 1664525 + 1013904223) >>> 0;
    return seed / 4294967296;
  };
  let landedIn = 0;
  for (let i = 0; i < 200; i++) {
    const fromX = CourtSpec.COURT_LEFT_X + 1.5 + seededRand() * (CourtSpec.COURT_WIDTH_FT - 3.0);
    const fromY = CourtSpec.UPPER_NVZ_Y + 0.5 + seededRand() * 14.0;
    const fromZ = 0.6 + seededRand() * 3.6;
    const playerX = CourtSpec.COURT_LEFT_X + 2.0 + seededRand() * (CourtSpec.COURT_WIDTH_FT - 4.0);
    const shot = computeBotReturnShot({
      fromX,
      fromY,
      fromZ,
      hitterY: fromY,
      targetSide: 'LOWER',
      opponentXs: [playerX],
      difficulty: 'NORMAL',
      randFn: seededRand,
    });
    const simBall: BallState = {
      x: fromX,
      y: fromY,
      z: fromZ,
      vx: shot.vx,
      vy: shot.vy,
      vz: shot.vz,
      active: true,
      bouncesOnCurrentSide: 0,
      lastHitterSide: 'UPPER',
      crossedNetSinceHit: false,
    };
    let shotIn = false;
    for (let step = 0; step < 180; step++) {
      const ev = stepBallPhysics(simBall, CourtSpec.FIXED_TIMESTEP_SEC);
      if (ev.netOutcome === 'HIT_NET') break;
      if (ev.bounced) {
        shotIn =
          simBall.crossedNetSinceHit &&
          simBall.y < CourtSpec.NET_Y &&
          CourtSpec.isInBounds(simBall.x, simBall.y);
        break;
      }
      if (
        simBall.x < -2 ||
        simBall.x > CourtSpec.WORLD_WIDTH_FT + 2 ||
        simBall.y < -3 ||
        simBall.y > CourtSpec.WORLD_HEIGHT_FT + 3
      ) {
        break;
      }
    }
    if (shotIn) landedIn += 1;
  }
  results.push({
    id: 'bot-ai-200-returns',
    name: 'Bot AI 200-return landing accuracy (>= 80% in bounds on Normal)',
    passed: landedIn >= 160,
    detail: `${landedIn} / 200 bot returns cleared net and landed inside court bounds`,
  });

  // Bot shot landing in the middle of the player's court (and on lines) must be "in";
  // if the player doesn't hit it back, point goes to bot (or side-out in side-out scoring).
  const runBotUnreturnedShot = (scoringMode: ScoringMode) => {
    const m = new SinglePlayerMatch('RIO', 'LOLA', scoringMode);
    m.launchServe();
    const afterServeHits = m.rallyHits; // 0 before 3rd shot
    // Advance until serve bounces on upper side and bot hits return
    for (let i = 0; i < 180; i++) {
      m.update(CourtSpec.FIXED_TIMESTEP_SEC);
      if (m.ball.lastHitterSide === 'UPPER' && m.rules.rallyShotCount === 2) {
        // Override velocity to aim cleanly into the middle of the player's court (14, 14)
        const v = computeTargetShotVelocity(m.ball.x, m.ball.y, m.ball.z, 14, 14, 'DRIVE', 0.5);
        m.ball.vx = v.vx;
        m.ball.vy = v.vy;
        m.ball.vz = v.vz;
        break;
      }
    }
    const afterReturnHits = m.rallyHits; // still 0 because 3rd shot hasn't been hit
    // Move player aside so player does not hit back
    m.setPlayerMoveTarget(2.0, 2.0);
    let resolution: PointResolution | null = null;
    for (let i = 0; i < 360; i++) {
      const outs = m.update(CourtSpec.FIXED_TIMESTEP_SEC);
      for (const o of outs) {
        if (o.pointResolution) {
          resolution = o.pointResolution;
          break;
        }
      }
      if (resolution) break;
    }
    return { m, resolution, afterServeHits, afterReturnHits };
  };

  const rallyUnreturned = runBotUnreturnedShot('RALLY');
  const sideOutUnreturned = runBotUnreturnedShot('SIDE_OUT');
  const lineHitOk =
    rallyUnreturned.m.rules.validateFirstBounce(CourtSpec.COURT_LEFT_X, CourtSpec.COURT_BOTTOM_Y, 'UPPER') === null &&
    rallyUnreturned.m.rules.validateFirstBounce(CourtSpec.COURT_RIGHT_X, 15, 'UPPER') === null;
  const botMidWinOk =
    lineHitOk &&
    rallyUnreturned.afterServeHits === 0 &&
    rallyUnreturned.afterReturnHits === 0 &&
    rallyUnreturned.m.bestRally === 2 &&
    rallyUnreturned.resolution !== null &&
    rallyUnreturned.resolution.faultReason !== 'OUT_OF_BOUNDS' &&
    rallyUnreturned.resolution.rallyWinner === 'UPPER' &&
    rallyUnreturned.resolution.pointAwardedTo === 'UPPER' &&
    rallyUnreturned.m.rules.scoreUpper === 1 &&
    sideOutUnreturned.resolution !== null &&
    sideOutUnreturned.resolution.rallyWinner === 'UPPER' &&
    sideOutUnreturned.resolution.sideOutOccurred &&
    sideOutUnreturned.m.rules.servingSide === 'UPPER';

  results.push({
    id: 'bot-in-bounds-winner',
    name: 'First-bounce in/out & unreturned bot winner (rally + side-out)',
    passed: botMidWinOk,
    detail: `Mid-court bot return awarded to UPPER (Rally ${rallyUnreturned.m.rules.scoreLower}-${rallyUnreturned.m.rules.scoreUpper}, Side-Out server=${sideOutUnreturned.m.rules.servingSide})`,
  });

  // 10 straight bot points with no player input yield exactly 0-10 and alternating serve boxes
  const streakMatch = new SinglePlayerMatch('RIO', 'LOLA', 'RALLY');
  streakMatch.autoSwingEnabled = false;
  let prevU = 0;
  let streakValid = true;
  const boxes: string[] = [];
  for (let i = 0; i < 60 * 60 && streakMatch.rules.scoreUpper < 10; i++) {
    if (streakMatch.rallyPhase === 'WAITING_SERVE' && streakMatch.rules.servingSide === 'LOWER') {
      streakMatch.launchServe();
    }
    const outs = streakMatch.update(CourtSpec.FIXED_TIMESTEP_SEC);
    for (const o of outs) {
      if (o.pointResolution) {
        if (streakMatch.rules.scoreLower !== 0 || streakMatch.rules.scoreUpper !== prevU + 1) {
          streakValid = false;
        }
        prevU = streakMatch.rules.scoreUpper;
        boxes.push(streakMatch.rules.currentServeAssignment().serverBox);
      }
    }
  }
  const boxesAlternate =
    boxes.length === 10 &&
    boxes.every((b, idx) => b === ((idx + 1) % 2 === 0 ? 'LEFT' : 'RIGHT'));
  results.push({
    id: 'solo-ten-bot-points-no-double',
    name: '10 straight bot points increment strictly +1 with alternating boxes',
    passed: streakValid && streakMatch.rules.scoreUpper === 10 && boxesAlternate,
    detail: `Final ${streakMatch.rules.scoreLower}-${streakMatch.rules.scoreUpper} over ${boxes.length} points`,
  });

  return results;
}

export function runPhase5PolishTests(): SpecTestResult[] {
  const results: SpecTestResult[] = [];

  const rosterOk =
    Object.keys(CHARACTER_PALETTES).length === 10 &&
    CHARACTER_PALETTES.LOLA.hasGlasses === true &&
    CHARACTER_PALETTES.BERT.hasBeard === true &&
    CHARACTER_PALETTES.BERT.bodyRadiusFt > CHARACTER_PALETTES.RIO.bodyRadiusFt &&
    CHARACTER_PALETTES.DILL.isPickleMascot === true &&
    CHARACTER_PALETTES.DILL.isUnlockable === true;

  results.push({
    id: 'character-roster-traits',
    name: 'Cosmetic character roster (Rio, Lola, Bert, Dill)',
    passed: rosterOk,
    detail: '4 distinct palettes · Lola glasses · Bert beard/big build · Dill unlockable',
  });

  const customMatch = new SinglePlayerMatch('BERT', 'DILL', 'RALLY');
  const cosmeticParityOk =
    customMatch.player.characterId === 'BERT' &&
    customMatch.bot.characterId === 'DILL' &&
    SinglePlayerMatch.PLAYER_SPEED_FT_S === 18.5;

  results.push({
    id: 'cosmetic-parity',
    name: 'Character selection applies without stat differences',
    passed: cosmeticParityOk,
    detail: 'Selected BERT vs DILL preserves equal 18.5 ft/s movement speed',
  });

  return results;
}


/** Diagnostic: simulate rallies per bot level with Dill chasing the ball, and classify
 *  every in-play bot shot Dill fails to return. Shown in the Project tab test list. */
export function runHitDiagnosticsTests(): SpecTestResult[] {
  const results: SpecTestResult[] = [];
  const levels: BotDifficulty[] = ['EASY', 'NORMAL', 'HARD', 'PRO'];
  const saved = matchFormat.difficulty;
  for (const level of levels) {
    matchFormat.difficulty = level;
    const sim = new SinglePlayerMatch('DILL', 'LOLA', 'RALLY');
    const dt = CourtSpec.FIXED_TIMESTEP_SEC;
    let balls = 0, hits = 0, notInRing = 0, tooHigh = 0, pastNet = 0, ruleBlocked = 0;
    let tracking = false, sawGeom = false, sawHigh = false, sawPastNet = false, sawReach = false;
    for (let i = 0; i < 60 * 240; i++) {
      if (sim.rallyPhase === 'WAITING_SERVE' && sim.rules.servingSide === 'LOWER') sim.launchServe();
      const b = sim.ball;
      const incoming = sim.rallyPhase === 'RALLY_LIVE' && b.active && b.lastHitterSide === 'UPPER';
      if (incoming && !tracking) {
        tracking = true; sawGeom = false; sawHigh = false; sawPastNet = false; sawReach = false;
      }
      if (incoming) {
        // Chase: line up with the ball's path; back off behind the bounce when it must bounce.
        const mustBounce = b.bouncesOnCurrentSide === 0 && (sim.rules.rallyShotCount <= 2);
        let ax = b.vy < -0.5 ? b.x + b.vx * Math.max(0, (sim.player.y - b.y) / b.vy) : b.x;
        let ay = sim.player.targetY;
        if (mustBounce && b.vy < -0.5) {
          const g = BallPhysicsConstants.GRAVITY_FT_S2;
          const t = (-b.vz - Math.sqrt(Math.max(0, b.vz * b.vz - 2 * g * b.z))) / g;
          ay = b.y + b.vy * t - 2.5;
          ax = b.x + b.vx * t;
        }
        sim.setPlayerMoveTarget(ax, ay);
        const d = Math.hypot(b.x - sim.player.x, b.y - sim.player.y);
        if (d <= SinglePlayerMatch.PLAYER_REACH_FT) {
          sawGeom = true;
          if (b.z > SinglePlayerMatch.MAX_HIT_HEIGHT_FT) sawHigh = true;
          if (b.y > CourtSpec.NET_Y + 0.8) sawPastNet = true;
          if (sim.canPlayerReachBall()) sawReach = true;
        }
      }
      const outs = sim.update(dt);
      const lostPoint = outs.some((o) => o.pointResolution?.rallyWinner === 'UPPER');
      if (tracking && (sim.ball.lastHitterSide === 'LOWER' || sim.rallyPhase !== 'RALLY_LIVE')) {
        tracking = false;
        if (sim.ball.lastHitterSide === 'LOWER' && sim.rallyPhase === 'RALLY_LIVE') {
          balls++;
          hits++;
        } else if (lostPoint) {
          // Bot's shot was good and Dill did not return it.
          balls++;
          if (sawReach) ruleBlocked++;
          else if (!sawGeom) notInRing++;
          else if (sawHigh) tooHigh++;
          else if (sawPastNet) pastNet++;
          else notInRing++;
        }
      }
      if ((sim.rallyPhase as RallyPhase) === 'MATCH_OVER') sim.resetMatch();
    }
    results.push({
      id: 'hit-diag-' + level.toLowerCase(),
      name: 'Hit diagnostics (info) vs ' + BOT_PROFILES[level].label,
      // Informational: some in-ring misses are expected (the two-bounce rule makes Dill wait).
      passed: true,
      detail: balls + ' balls · returned ' + hits + ' · missed: not in ring ' + notInRing +
        ', too high ' + tooHigh + ', past net ' + pastNet + ', in ring but blocked by rules ' + ruleBlocked,
    });
  }
  matchFormat.difficulty = saved;
  return results;
}
