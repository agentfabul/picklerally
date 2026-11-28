import { useEffect, useRef } from 'react';
import {
  BallPhysicsConstants,
  CHARACTER_PALETTES,
  SinglePlayerMatch,
  spriteScaleForHeight,
  type CharacterId,
  type PlayerEntity,
  type RallyPhase,
  type ShotType,
} from '../game/ballPhysics';
import { CourtColors, CourtSpec, type CourtSide } from '../game/courtSpec';
import { DoublesMatch } from '../game/doublesMatch';
import { OnlineMatch1v1 } from '../game/onlineMatch1v1';
import type { OnlineRoomSession } from '../game/onlineRoomService';
import type { ScoringMode } from '../game/rulesEngine';
import { soundFx } from '../game/soundFx';

export interface HudSnapshot {
  rallyPhase: RallyPhase;
  scoreLower: number;
  scoreUpper: number;
  servingSide: CourtSide;
  serverNumberCall: number;
  serverIndexInTeam: number;
  formattedScoreCall: string;
  rallyShotCount: number;
  ballBouncesOnSide: number;
  botServeCountdownSec: number;
  rallyHits: number;
  bestRally: number;
  lastCallout: string;
  lastShotLabel: string;
  inReach: boolean;
  matchWinner: CourtSide | null;
  hostDisconnected: boolean;
  /** Solo best-of-3 progress, e.g. "Game 2 · Games 1-0" (empty for single games). */
  matchStatus: string;
  gamesLower: number;
  gamesUpper: number;
  gameScoresSummary: string;
}

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  life: number;
  maxLife: number;
  color: string;
}

interface CourtCanvasProps {
  scoringMode: ScoringMode;
  matchMode: 'SINGLES' | 'DOUBLES';
  playerCharacter: CharacterId;
  partnerCharacter: CharacterId;
  opponentCharacter: CharacterId;
  showDimensions: boolean;
  serveRequestNonce: number;
  receiveReadyNonce?: number;
  resetRequestNonce: number;
  paused: boolean;
  onlineSession: OnlineRoomSession | null;
  simulatedGuestActive: boolean;
  playerName?: string;
  opponentName?: string;
  onHudChange: (hud: HudSnapshot) => void;
}

export function CourtCanvas({
  scoringMode,
  matchMode,
  playerCharacter,
  partnerCharacter,
  opponentCharacter,
  showDimensions,
  serveRequestNonce,
  receiveReadyNonce = 0,
  resetRequestNonce,
  paused,
  onlineSession,
  simulatedGuestActive,
  onHudChange,
  playerName,
  opponentName,
}: CourtCanvasProps) {
  const namesRef = useRef({ player: playerName, opponent: opponentName, room: onlineSession?.room });
  namesRef.current = { player: playerName, opponent: opponentName, room: onlineSession?.room };
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const showDimensionsRef = useRef(showDimensions);
  showDimensionsRef.current = showDimensions;

  const pausedRef = useRef(paused);
  pausedRef.current = paused;

  const simGuestRef = useRef(simulatedGuestActive);
  simGuestRef.current = simulatedGuestActive;

  const onHudChangeRef = useRef(onHudChange);
  onHudChangeRef.current = onHudChange;

  const soloMatchRef = useRef<SinglePlayerMatch>(
    new SinglePlayerMatch(playerCharacter, opponentCharacter, scoringMode),
  );
  const doublesMatchRef = useRef<DoublesMatch | null>(null);
  const onlineMatchRef = useRef<OnlineMatch1v1 | null>(null);
  const sessionRef = useRef<OnlineRoomSession | null>(onlineSession);
  sessionRef.current = onlineSession;

  const secondaryOpponentChar = (opp: CharacterId): CharacterId =>
    opp === 'BERT' ? 'LOLA' : 'BERT';

  // Rebuild solo, doubles, or online simulation when mode/session/characters change
  useEffect(() => {
    if (onlineSession) {
      doublesMatchRef.current = null;
      onlineMatchRef.current = new OnlineMatch1v1(
        onlineSession.room.isHost,
        onlineSession.room.localUid,
        playerCharacter,
        opponentCharacter,
        scoringMode,
      );
    } else if (matchMode === 'DOUBLES') {
      onlineMatchRef.current = null;
      doublesMatchRef.current = new DoublesMatch(
        playerCharacter,
        partnerCharacter,
        opponentCharacter,
        secondaryOpponentChar(opponentCharacter),
        scoringMode,
      );
    } else {
      onlineMatchRef.current = null;
      doublesMatchRef.current = null;
      soloMatchRef.current = new SinglePlayerMatch(
        playerCharacter,
        opponentCharacter,
        scoringMode,
      );
    }
  }, [onlineSession, matchMode, scoringMode, playerCharacter, partnerCharacter, opponentCharacter]);

  useEffect(() => {
    if (serveRequestNonce === 0) return;
    const om = onlineMatchRef.current;
    const sess = sessionRef.current;
    if (om && sess) {
      if (om.rallyPhase === 'WAITING_SERVE' && om.servingLocal) {
        const res = om.triggerLocalSwipe({ dxFt: 0, dyFt: 3.4, durationSec: 0.2 });
        if (res.guestInput) sess.sendInput(res.guestInput);
        if (res.hit) soundFx.playPaddleHit('SERVE', false);
        else if (res.fault) soundFx.playPointEnd(res.fault.calloutText);
      }
      return;
    }

    const dm = doublesMatchRef.current;
    const res = dm ? dm.launchServe() : soloMatchRef.current.launchServe();
    if (res.hit) {
      soundFx.playPaddleHit('SERVE', false);
    } else if (res.fault) {
      soundFx.playPointEnd(res.fault.calloutText);
    }
  }, [serveRequestNonce]);

  useEffect(() => {
    if (receiveReadyNonce === 0) return;
    if (onlineMatchRef.current) return;
    const dm = doublesMatchRef.current;
    const res = dm
      ? dm.triggerReadyAiServe()
      : soloMatchRef.current.triggerReadyBotServe();
    if (res.hit) {
      soundFx.playPaddleHit('SERVE', true);
    } else if (res.fault) {
      soundFx.playPointEnd(res.fault.calloutText);
    }
  }, [receiveReadyNonce]);

  useEffect(() => {
    if (resetRequestNonce === 0) return;
    if (onlineSession) {
      doublesMatchRef.current = null;
      onlineMatchRef.current = new OnlineMatch1v1(
        onlineSession.room.isHost,
        onlineSession.room.localUid,
        playerCharacter,
        opponentCharacter,
        scoringMode,
      );
    } else if (matchMode === 'DOUBLES') {
      doublesMatchRef.current = new DoublesMatch(
        playerCharacter,
        partnerCharacter,
        opponentCharacter,
        secondaryOpponentChar(opponentCharacter),
        scoringMode,
      );
    } else {
      doublesMatchRef.current = null;
      soloMatchRef.current = new SinglePlayerMatch(
        playerCharacter,
        opponentCharacter,
        scoringMode,
      );
    }
  }, [
    resetRequestNonce,
    matchMode,
    scoringMode,
    onlineSession,
    playerCharacter,
    partnerCharacter,
    opponentCharacter,
  ]);

  // Expose live callbacks from OnlineRoomSession to OnlineMatch1v1
  useEffect(() => {
    if (!onlineSession) return;
    const win = window as unknown as {
      __pickleOnlineBridge?: {
        onRemoteInput: (inp: Parameters<OnlineMatch1v1['applyRemoteGuestInput']>[0]) => void;
        onHostSnapshot: (snap: Parameters<OnlineMatch1v1['applyHostSnapshot']>[0]) => void;
        onRoomStatusChange: (status: Parameters<OnlineMatch1v1['onRoomMetaUpdated']>[0]) => void;
      };
    };
    win.__pickleOnlineBridge = {
      onRemoteInput: (inp) => onlineMatchRef.current?.applyRemoteGuestInput(inp),
      onHostSnapshot: (snap) => onlineMatchRef.current?.applyHostSnapshot(snap),
      onRoomStatusChange: (status) => onlineMatchRef.current?.onRoomMetaUpdated(status),
    };
    return () => {
      delete win.__pickleOnlineBridge;
    };
  }, [onlineSession]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let rafId = 0;
    let lastTime = performance.now();
    let elapsedSec = 0;
    let hitstopTimer = 0;
    // Why the player can or can't hit this frame (solo matches), for the ring color/hint.
    let currentHitState: 'NONE' | 'READY' | 'WAIT_BOUNCE' | 'KITCHEN' | 'TOO_HIGH' = 'NONE';
    let shakeIntensity = 0;
    let calloutAlpha = 0;
    let lastHudKey = '';
    let lastInputPushSec = 0;
    let simGuestSeq = 1;
    let simGuestCooldownSec = 0;

    const particles: Particle[] = Array.from({ length: 28 }, () => ({
      x: 0,
      y: 0,
      vx: 0,
      vy: 0,
      life: 0,
      maxLife: 0.3,
      color: '#FACC15',
    }));

    const spawnBurst = (wx: number, wy: number, color: string, count: number, speed: number) => {
      let spawned = 0;
      for (let i = 0; i < particles.length && spawned < count; i++) {
        if (particles[i].life <= 0) {
          const angle = (Math.PI * 2 * spawned) / count + Math.random() * 0.5;
          const mag = speed * (0.55 + Math.random() * 0.65);
          particles[i].x = wx;
          particles[i].y = wy;
          particles[i].vx = Math.cos(angle) * mag;
          particles[i].vy = Math.sin(angle) * mag;
          particles[i].maxLife = 0.24 + Math.random() * 0.14;
          particles[i].life = particles[i].maxLife;
          particles[i].color = color;
          spawned++;
        }
      }
    };

    const heldKeys = new Set<string>();
    let pointerActive = false;
    let swipeConsumed = false;
    let swipeStartX = 0;
    let swipeStartY = 0;
    let swipeCurX = 0;
    let swipeCurY = 0;
    let swipeStartTime = 0;

    const syncCanvasSize = () => {
      const rect = container.getBoundingClientRect();
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const targetW = Math.max(1, Math.floor(rect.width * dpr));
      const targetH = Math.max(1, Math.floor(rect.height * dpr));
      if (canvas.width !== targetW || canvas.height !== targetH) {
        canvas.width = targetW;
        canvas.height = targetH;
      }
    };

    const resizeObserver = new ResizeObserver(syncCanvasSize);
    resizeObserver.observe(container);
    syncCanvasSize();

    const getViewportMetrics = () => {
      const cw = canvas.width;
      const ch = canvas.height;
      const worldAspect = CourtSpec.WORLD_WIDTH_FT / CourtSpec.WORLD_HEIGHT_FT;
      const screenAspect = cw / ch;

      let drawW = cw;
      let drawH = ch;
      let offsetX = 0;
      let offsetY = 0;

      if (screenAspect > worldAspect) {
        drawW = ch * worldAspect;
        offsetX = (cw - drawW) / 2;
      } else {
        drawH = cw / worldAspect;
        offsetY = (ch - drawH) / 2;
      }

      const scale = drawW / CourtSpec.WORLD_WIDTH_FT;
      return { cw, ch, drawW, drawH, offsetX, offsetY, scale };
    };

    const worldToScreen = (
      wx: number,
      wy: number,
      m: ReturnType<typeof getViewportMetrics>,
    ) => ({
      sx: m.offsetX + wx * m.scale,
      sy: m.offsetY + (CourtSpec.WORLD_HEIGHT_FT - wy) * m.scale,
    });

    const screenToWorld = (clientX: number, clientY: number) => {
      const rect = canvas.getBoundingClientRect();
      const dpr = canvas.width / Math.max(1, rect.width);
      const px = (clientX - rect.left) * dpr;
      const py = (clientY - rect.top) * dpr;
      const m = getViewportMetrics();

      const wx = Math.max(
        0,
        Math.min(CourtSpec.WORLD_WIDTH_FT, (px - m.offsetX) / m.scale),
      );
      const wy = Math.max(
        0,
        Math.min(
          CourtSpec.WORLD_HEIGHT_FT,
          CourtSpec.WORLD_HEIGHT_FT - (py - m.offsetY) / m.scale,
        ),
      );

      return { wx, wy };
    };

    const triggerHitEffects = (shotType: ShotType, hitX: number, hitY: number, isUpper: boolean) => {
      soundFx.playPaddleHit(shotType, isUpper);
      calloutAlpha = 1;
      if (shotType === 'DRIVE') {
        hitstopTimer = 0.035;
        shakeIntensity = 0.24;
        spawnBurst(hitX, hitY, '#F97316', 10, 8.5);
      } else if (shotType === 'LOB') {
        spawnBurst(hitX, hitY, '#38BDF8', 7, 5.5);
      } else {
        spawnBurst(hitX, hitY, '#84CC16', 6, 4.2);
      }
    };

    const triggerFaultEffects = (
      callout: string,
      pointScoredBy: CourtSide | null = null,
      matchWinner: CourtSide | null = null,
    ) => {
      soundFx.playPointEnd(callout);
      if (matchWinner !== null) {
        soundFx.playMatchVictory(matchWinner === 'LOWER');
      } else if (pointScoredBy !== null) {
        soundFx.playPointScored(pointScoredBy === 'LOWER');
      }
      calloutAlpha = 1.25;
      shakeIntensity = 0.22;
    };

    const pushLocalMove = (wx: number, wy: number, force = false) => {
      const om = onlineMatchRef.current;
      const sess = sessionRef.current;
      const dm = doublesMatchRef.current;
      if (om && sess) {
        const inp = om.setLocalMoveTarget(wx, wy);
        if (!om.isHost && (force || elapsedSec - lastInputPushSec >= 0.045)) {
          lastInputPushSec = elapsedSec;
          sess.sendInput(inp);
        }
      } else if (dm) {
        dm.setPlayerMoveTarget(wx, wy);
      } else {
        soloMatchRef.current.setPlayerMoveTarget(wx, wy);
      }
    };

    const attemptLocalSwipe = (dx: number, dy: number, duration: number, forced?: ShotType) => {
      const om = onlineMatchRef.current;
      const sess = sessionRef.current;
      const dm = doublesMatchRef.current;
      if (om && sess) {
        const res = om.triggerLocalSwipe({ dxFt: dx, dyFt: dy, durationSec: duration });
        if (res.guestInput) sess.sendInput(res.guestInput);
        if (res.hit) {
          triggerHitEffects(res.hit.shotType, res.hit.hitX, res.hit.hitY, false);
          return true;
        }
        if (res.fault) {
          triggerFaultEffects(res.fault.calloutText, res.fault.pointAwardedTo, res.fault.matchWinner);
          return true;
        }
        return false;
      }

      const res = dm
        ? dm.attemptPlayerSwipeHit({ dxFt: dx, dyFt: dy, durationSec: duration }, forced)
        : soloMatchRef.current.attemptPlayerSwipeHit(
            { dxFt: dx, dyFt: dy, durationSec: duration },
            forced,
          );
      if (res.hit) {
        triggerHitEffects(res.hit.shotType, res.hit.hitX, res.hit.hitY, false);
        return true;
      }
      if (res.fault) {
        triggerFaultEffects(res.fault.calloutText, res.fault.pointAwardedTo, res.fault.matchWinner);
        return true;
      }
      return false;
    };

    const handlePointerDown = (e: PointerEvent) => {
      if (pausedRef.current) return;
      canvas.setPointerCapture(e.pointerId);
      const { wx, wy } = screenToWorld(e.clientX, e.clientY);
      pointerActive = true;
      swipeConsumed = false;
      swipeStartX = wx;
      swipeStartY = wy;
      swipeCurX = wx;
      swipeCurY = wy;
      swipeStartTime = elapsedSec;

      if (wy <= CourtSpec.NET_Y + 2) {
        pushLocalMove(wx, wy, true);
      }
    };

    const handlePointerMove = (e: PointerEvent) => {
      if (!pointerActive || pausedRef.current) return;
      const { wx, wy } = screenToWorld(e.clientX, e.clientY);
      swipeCurX = wx;
      swipeCurY = wy;

      const dx = wx - swipeStartX;
      const dy = wy - swipeStartY;
      const dist = Math.hypot(dx, dy);
      const duration = Math.max(0.04, elapsedSec - swipeStartTime);
      const speed = dist / duration;

      if (!swipeConsumed && dist >= 1.25 && speed >= 7.2) {
        if (attemptLocalSwipe(dx, dy, duration)) {
          swipeConsumed = true;
          return;
        }
      }

      if (wy <= CourtSpec.NET_Y + 2) {
        pushLocalMove(wx, wy, false);
      }
    };

    const handlePointerUp = (e: PointerEvent) => {
      if (!pointerActive) return;
      if (canvas.hasPointerCapture(e.pointerId)) {
        canvas.releasePointerCapture(e.pointerId);
      }
      if (!pausedRef.current && !swipeConsumed) {
        const { wx, wy } = screenToWorld(e.clientX, e.clientY);
        const dx = wx - swipeStartX;
        const dy = wy - swipeStartY;
        const dist = Math.hypot(dx, dy);
        const duration = Math.max(0.04, elapsedSec - swipeStartTime);

        if (dist >= 1.1) {
          attemptLocalSwipe(dx, dy, duration);
        }
      }
      pointerActive = false;
      swipeConsumed = false;
    };

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
      const key = e.key.toLowerCase();
      heldKeys.add(key);

      if (pausedRef.current) return;

      const om = onlineMatchRef.current;
      if (key === ' ' || key === '1' || key === '2' || key === '3') {
        if (key === ' ') e.preventDefault();
        if (om) {
          if (om.rallyPhase === 'WAITING_SERVE' && om.servingLocal) {
            attemptLocalSwipe(0, 3.4, 0.2);
          } else if (om.canLocalReachBall()) {
            const aimX = (CourtSpec.COURT_CENTER_X - om.localPlayer.x) * 0.5;
            if (key === '1') attemptLocalSwipe(aimX, 1.8, 0.25);
            else if (key === '3') attemptLocalSwipe(aimX * 0.4, 3.8, 0.2);
            else attemptLocalSwipe(aimX, 6.2, 0.16);
          }
          return;
        }

        const dm = doublesMatchRef.current;
        if (dm) {
          if (
            dm.rallyPhase === 'WAITING_SERVE' &&
            dm.activeServerSide() === 'LOWER' &&
            dm.activeServerIndex() === 0
          ) {
            const res = dm.launchServe();
            if (res.hit) triggerHitEffects('SERVE', res.hit.hitX, res.hit.hitY, false);
            else if (res.fault) triggerFaultEffects(res.fault.calloutText, res.fault.pointAwardedTo, res.fault.matchWinner);
          } else if (dm.canPlayerReachBall()) {
            const forced: ShotType = key === '1' ? 'DINK' : key === '3' ? 'LOB' : 'DRIVE';
            const aimX = (CourtSpec.COURT_CENTER_X - dm.player.x) * 0.5;
            const swipe =
              forced === 'DINK'
                ? { dxFt: aimX, dyFt: 1.8, durationSec: 0.25 }
                : forced === 'LOB'
                  ? { dxFt: aimX * 0.4, dyFt: 3.8, durationSec: 0.2 }
                  : { dxFt: aimX, dyFt: 6.2, durationSec: 0.16 };
            attemptLocalSwipe(swipe.dxFt, swipe.dyFt, swipe.durationSec, forced);
          }
          return;
        }

        const match = soloMatchRef.current;
        if (match.rallyPhase === 'WAITING_SERVE' && match.rules.servingSide === 'LOWER') {
          const res = match.launchServe();
          if (res.hit) triggerHitEffects('SERVE', res.hit.hitX, res.hit.hitY, false);
          else if (res.fault) triggerFaultEffects(res.fault.calloutText, res.fault.pointAwardedTo, res.fault.matchWinner);
        } else if (match.canPlayerReachBall()) {
          const forced: ShotType = key === '1' ? 'DINK' : key === '3' ? 'LOB' : 'DRIVE';
          const aimX = (CourtSpec.COURT_CENTER_X - match.player.x) * 0.5;
          const swipe =
            forced === 'DINK'
              ? { dxFt: aimX, dyFt: 1.8, durationSec: 0.25 }
              : forced === 'LOB'
                ? { dxFt: aimX * 0.4, dyFt: 3.8, durationSec: 0.2 }
                : { dxFt: aimX, dyFt: 6.2, durationSec: 0.16 };
          attemptLocalSwipe(swipe.dxFt, swipe.dyFt, swipe.durationSec, forced);
        }
      }
    };

    const handleKeyUp = (e: KeyboardEvent) => {
      heldKeys.delete(e.key.toLowerCase());
    };

    const handleBlur = () => {
      heldKeys.clear();
      pointerActive = false;
    };

    canvas.addEventListener('pointerdown', handlePointerDown);
    canvas.addEventListener('pointermove', handlePointerMove);
    canvas.addEventListener('pointerup', handlePointerUp);
    canvas.addEventListener('pointercancel', handlePointerUp);
    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);
    window.addEventListener('blur', handleBlur);

    const drawDimensionTag = (sx: number, sy: number, label: string, dprScale: number) => {
      ctx.save();
      ctx.font = `600 ${Math.round(10 * dprScale)}px "JetBrains Mono", monospace`;
      const metrics = ctx.measureText(label);
      const padX = 5 * dprScale;
      const boxW = metrics.width + padX * 2;
      const boxH = 16 * dprScale;
      ctx.fillStyle = 'rgba(11, 18, 32, 0.88)';
      ctx.fillRect(sx - boxW / 2, sy - boxH / 2, boxW, boxH);
      ctx.strokeStyle = 'rgba(248, 250, 252, 0.25)';
      ctx.lineWidth = Math.max(1, dprScale);
      ctx.strokeRect(sx - boxW / 2, sy - boxH / 2, boxW, boxH);
      ctx.fillStyle = '#F8FAFC';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(label, sx, sy + 0.5 * dprScale);
      ctx.restore();
    };

    const drawCharacter = (
      entity: PlayerEntity,
      m: ReturnType<typeof getViewportMetrics>,
      inReach: boolean,
      showHitRingGuide = false,
      nameOverride?: string,
      characterOverride?: CharacterId,
    ) => {
      const pal = CHARACTER_PALETTES[characterOverride ?? entity.characterId];
      const pos = worldToScreen(entity.x, entity.y, m);
      const facingUp = entity.side === 'LOWER';
      const facingSign = facingUp ? -1 : 1;

      if (entity.side === 'LOWER' && (inReach || showHitRingGuide || currentHitState !== 'NONE')) {
        ctx.save();
        const hitBlocked =
          currentHitState === 'WAIT_BOUNCE' || currentHitState === 'KITCHEN' || currentHitState === 'TOO_HIGH';
        if (hitBlocked) {
          // Ball is in the ring but a hit isn't allowed yet: orange ring + reason.
          ctx.beginPath();
          ctx.arc(pos.sx, pos.sy, SinglePlayerMatch.PLAYER_REACH_FT * m.scale, 0, Math.PI * 2);
          ctx.fillStyle = 'rgba(249, 115, 22, 0.12)';
          ctx.fill();
          ctx.strokeStyle = 'rgba(251, 146, 60, 0.9)';
          ctx.lineWidth = Math.max(2, 0.12 * m.scale);
          ctx.setLineDash([0.32 * m.scale, 0.2 * m.scale]);
          ctx.stroke();
          ctx.setLineDash([]);
          const hint =
            currentHitState === 'WAIT_BOUNCE'
              ? 'Let it bounce'
              : currentHitState === 'KITCHEN'
                ? 'Step out of the kitchen'
                : 'Too high';
          ctx.font = `700 ${Math.max(10, 0.62 * m.scale)}px ui-sans-serif, system-ui, sans-serif`;
          ctx.textAlign = 'center';
          ctx.textBaseline = 'bottom';
          ctx.fillStyle = '#FDBA74';
          ctx.fillText(hint, pos.sx, pos.sy - (SinglePlayerMatch.PLAYER_REACH_FT + 0.3) * m.scale);
        } else if (inReach) {
          const pulse = 0.5 + 0.5 * Math.sin(elapsedSec * 12);
          const ringRadius = (SinglePlayerMatch.PLAYER_REACH_FT - 0.35 + pulse * 0.35) * m.scale;
          ctx.beginPath();
          ctx.arc(pos.sx, pos.sy, ringRadius, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(132, 204, 22, ${0.14 + pulse * 0.16})`;
          ctx.fill();
          ctx.strokeStyle = `rgba(163, 230, 53, ${0.72 + pulse * 0.28})`;
          ctx.lineWidth = Math.max(2, (0.1 + pulse * 0.08) * m.scale);
          ctx.setLineDash([0.32 * m.scale, 0.2 * m.scale]);
          ctx.stroke();

          // Outward expanding ripple ring when ball is within reach
          const ripplePhase = (elapsedSec * 1.9) % 1;
          ctx.beginPath();
          ctx.arc(
            pos.sx,
            pos.sy,
            (SinglePlayerMatch.PLAYER_REACH_FT + ripplePhase * 1.05) * m.scale,
            0,
            Math.PI * 2,
          );
          ctx.setLineDash([]);
          ctx.strokeStyle = `rgba(163, 230, 53, ${(1 - ripplePhase) * 0.55})`;
          ctx.lineWidth = Math.max(1.2, 0.06 * m.scale);
          ctx.stroke();
        } else {
          ctx.beginPath();
          ctx.arc(pos.sx, pos.sy, SinglePlayerMatch.PLAYER_REACH_FT * m.scale, 0, Math.PI * 2);
          ctx.strokeStyle = 'rgba(248, 250, 252, 0.16)';
          ctx.lineWidth = Math.max(1, 0.05 * m.scale);
          ctx.setLineDash([0.28 * m.scale, 0.28 * m.scale]);
          ctx.stroke();
        }
        ctx.restore();
      }

      ctx.save();
      ctx.fillStyle = 'rgba(7, 11, 20, 0.34)';
      ctx.beginPath();
      ctx.ellipse(
        pos.sx,
        pos.sy + 0.32 * m.scale,
        1.18 * m.scale,
        0.62 * m.scale,
        0,
        0,
        Math.PI * 2,
      );
      ctx.fill();

      const swingProg = entity.swingTimer > 0 ? 1 - entity.swingTimer / 0.24 : 0;
      const baseAngle = facingUp ? -0.25 : Math.PI - 0.25;
      const paddleAngle = baseAngle + (facingUp ? -1 : 1) * swingProg * 1.45;
      const paddleDist = 1.48 * m.scale;
      const px = pos.sx + Math.cos(paddleAngle) * paddleDist;
      const py = pos.sy + Math.sin(paddleAngle) * paddleDist;

      ctx.strokeStyle = '#334155';
      ctx.lineWidth = Math.max(2, 0.2 * m.scale);
      ctx.beginPath();
      ctx.moveTo(
        pos.sx + Math.cos(paddleAngle) * 0.7 * m.scale,
        pos.sy + Math.sin(paddleAngle) * 0.7 * m.scale,
      );
      ctx.lineTo(px, py);
      ctx.stroke();

      ctx.fillStyle = pal.paddle;
      ctx.beginPath();
      ctx.arc(px, py, 0.54 * m.scale, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#0F172A';
      ctx.lineWidth = Math.max(1.4, 0.08 * m.scale);
      ctx.stroke();

      ctx.fillStyle = pal.shirt;
      ctx.beginPath();
      ctx.arc(pos.sx, pos.sy, pal.bodyRadiusFt * m.scale, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#0F172A';
      ctx.lineWidth = Math.max(1.4, 0.08 * m.scale);
      ctx.stroke();

      const headY = pos.sy + facingSign * 0.18 * m.scale;
      ctx.fillStyle = pal.skin;
      ctx.beginPath();
      ctx.arc(pos.sx, headY, 0.72 * m.scale, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#0F172A';
      ctx.stroke();

      if (pal.isPickleMascot) {
        ctx.fillStyle = 'rgba(63, 98, 18, 0.55)';
        ctx.beginPath();
        ctx.arc(pos.sx - 0.34 * m.scale, headY - facingSign * 0.2 * m.scale, 0.14 * m.scale, 0, Math.PI * 2);
        ctx.arc(pos.sx + 0.28 * m.scale, headY - facingSign * 0.12 * m.scale, 0.12 * m.scale, 0, Math.PI * 2);
        ctx.fill();
      } else {
        ctx.fillStyle = pal.hair;
        ctx.beginPath();
        ctx.arc(
          pos.sx,
          headY - facingSign * 0.12 * m.scale,
          0.69 * m.scale,
          facingUp ? 0 : Math.PI,
          facingUp ? Math.PI : Math.PI * 2,
        );
        ctx.fill();
      }

      if (pal.hasBeard) {
        ctx.fillStyle = pal.hair;
        ctx.beginPath();
        ctx.arc(
          pos.sx,
          headY + facingSign * 0.3 * m.scale,
          0.48 * m.scale,
          facingUp ? Math.PI : 0,
          facingUp ? Math.PI * 2 : Math.PI,
        );
        ctx.fill();
      }

      ctx.fillStyle = pal.headwear;
      ctx.fillRect(
        pos.sx - 0.67 * m.scale,
        headY + facingSign * 0.22 * m.scale - 0.14 * m.scale,
        1.34 * m.scale,
        0.28 * m.scale,
      );

      if (entity.characterId === 'RIO') {
        ctx.fillRect(pos.sx + 0.52 * m.scale, headY - facingSign * 0.08 * m.scale, 0.32 * m.scale, 0.18 * m.scale);
      }

      if (pal.hasGlasses) {
        ctx.strokeStyle = '#0F172A';
        ctx.lineWidth = Math.max(1.2, 0.07 * m.scale);
        const eyeY = headY + facingSign * 0.38 * m.scale;
        ctx.strokeRect(
          pos.sx - 0.42 * m.scale,
          eyeY - 0.1 * m.scale,
          0.3 * m.scale,
          0.2 * m.scale,
        );
        ctx.strokeRect(
          pos.sx + 0.12 * m.scale,
          eyeY - 0.1 * m.scale,
          0.3 * m.scale,
          0.2 * m.scale,
        );
      }

      ctx.fillStyle = 'rgba(248, 250, 252, 0.86)';
      ctx.font = `600 ${Math.max(10, Math.round(0.68 * m.scale))}px "Plus Jakarta Sans", sans-serif`;
      ctx.textAlign = 'center';
      ctx.fillText(nameOverride || pal.name, pos.sx, pos.sy + (facingUp ? 1.78 : -1.52) * m.scale);

      ctx.restore();
    };

    const renderFrame = (now: number) => {
      const rawDt = (now - lastTime) / 1000;
      const dt = Math.min(rawDt, 0.05);
      lastTime = now;

      const om = onlineMatchRef.current;
      const sess = sessionRef.current;
      const dm = doublesMatchRef.current;
      const solo = soloMatchRef.current;

      if (!pausedRef.current) {
        elapsedSec += dt;
        if (simGuestCooldownSec > 0) simGuestCooldownSec = Math.max(0, simGuestCooldownSec - dt);

        let kdx = 0;
        let kdy = 0;
        if (heldKeys.has('a') || heldKeys.has('arrowleft')) kdx -= 1;
        if (heldKeys.has('d') || heldKeys.has('arrowright')) kdx += 1;
        if (heldKeys.has('w') || heldKeys.has('arrowup')) kdy += 1;
        if (heldKeys.has('s') || heldKeys.has('arrowdown')) kdy -= 1;
        if (kdx !== 0 || kdy !== 0) {
          const len = Math.hypot(kdx, kdy);
          const baseP = om ? om.localPlayer : dm ? dm.player : solo.player;
          pushLocalMove(baseP.x + (kdx / len) * 2.5, baseP.y + (kdy / len) * 2.5, false);
        }

        // Optional simulated remote guest driver when Host tests solo in browser
        if (om && om.isHost && simGuestRef.current && !om.hostDisconnected) {
          const mirrorGuestX = OnlineMatch1v1.mirrorX(om.ball.x);
          const targetGuestY =
            om.rallyPhase === 'WAITING_SERVE' && !om.servingLocal
              ? 4.9
              : Math.min(18, Math.max(6, OnlineMatch1v1.mirrorY(om.ball.y) - 2));
          let swingType: string | null = null;
          let aimX = 0;
          let aimY = 0;

          if (om.rallyPhase === 'WAITING_SERVE' && !om.servingLocal && simGuestCooldownSec <= 0) {
            swingType = 'SERVE';
            aimX = 0;
            aimY = 3.5;
            simGuestCooldownSec = 0.85;
          } else if (
            om.rallyPhase === 'RALLY_LIVE' &&
            om.ball.lastHitterSide === 'LOWER' &&
            om.ball.crossedNetSinceHit &&
            om.ball.bouncesOnCurrentSide >= (om.rules.rallyShotCount === 1 ? 1 : 0) &&
            Math.hypot(om.ball.x - om.remotePlayer.x, om.ball.y - om.remotePlayer.y) <= 3.9 &&
            simGuestCooldownSec <= 0
          ) {
            swingType = 'DRIVE';
            aimX = om.localPlayer.x > CourtSpec.COURT_CENTER_X ? 2.2 : -2.2;
            aimY = 4.6;
            simGuestCooldownSec = 0.45;
          }

          simGuestSeq += 1;
          om.applyRemoteGuestInput({
            uid: 'sim-guest',
            sequence: simGuestSeq,
            targetX: Math.max(4, Math.min(24, mirrorGuestX)),
            targetY: Math.max(3, Math.min(20, targetGuestY)),
            swingType,
            aimX,
            aimY,
            swipeDurationSec: 0.2,
            timestampMs: Date.now(),
          });
        }

        if (hitstopTimer > 0) {
          hitstopTimer = Math.max(0, hitstopTimer - dt);
        } else if (om && sess) {
          const res = om.update(dt);
          if (res.snapshotToPublish && om.isHost) {
            sess.publishState(res.snapshotToPublish);
          }
          if (res.stepEvents.bounced) {
            soundFx.playBounce(res.stepEvents.bounceZSpeed);
            spawnBurst(res.stepEvents.bounceX, res.stepEvents.bounceY, '#F8FAFC', 4, 2.5);
          }
          if (res.hitFeedback) {
            triggerHitEffects(
              res.hitFeedback.shotType,
              res.hitFeedback.hitX,
              res.hitFeedback.hitY,
              res.hitFeedback.hitterSide === 'UPPER',
            );
          }
          if (res.pointResolution) {
            triggerFaultEffects(
              res.pointResolution.calloutText,
              res.pointResolution.pointAwardedTo,
              res.pointResolution.matchWinner,
            );
          }
        } else {
          const ticks = dm ? dm.update(dt) : solo.update(dt);
          for (const tick of ticks) {
            if (tick.stepEvents.bounced) {
              soundFx.playBounce(tick.stepEvents.bounceZSpeed);
              spawnBurst(tick.stepEvents.bounceX, tick.stepEvents.bounceY, '#F8FAFC', 4, 2.5);
            }
            if (tick.hitFeedback) {
              triggerHitEffects(
                tick.hitFeedback.shotType,
                tick.hitFeedback.hitX,
                tick.hitFeedback.hitY,
                tick.hitFeedback.hitterSide === 'UPPER',
              );
            }
            if (tick.pointResolution) {
              triggerFaultEffects(
                tick.pointResolution.calloutText,
                tick.pointResolution.pointAwardedTo,
                tick.pointResolution.matchWinner,
              );
            }
          }
        }

        if (shakeIntensity > 0) {
          shakeIntensity = Math.max(0, shakeIntensity - dt * 1.8);
        }
        if (calloutAlpha > 0) {
          calloutAlpha = Math.max(0, calloutAlpha - dt * 0.72);
        }

        for (let i = 0; i < particles.length; i++) {
          const p = particles[i];
          if (p.life > 0) {
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life -= dt;
          }
        }
      }

      const activePhase = om ? om.rallyPhase : dm ? dm.rallyPhase : solo.rallyPhase;
      const scoreLower = om ? om.scoreLocal : dm ? dm.rules.scoreLower : solo.rules.scoreLower;
      const scoreUpper = om ? om.scoreRemote : dm ? dm.rules.scoreUpper : solo.rules.scoreUpper;
      const servingSide: CourtSide = om
        ? om.servingLocal
          ? 'LOWER'
          : 'UPPER'
        : dm
          ? dm.rules.servingSide
          : solo.rules.servingSide;
      const serverNumberCall = om ? 1 : dm ? dm.rules.serverNumberCall : 1;
      const serverIndexInTeam = om ? 0 : dm ? dm.activeServerIndex() : 0;
      const formattedScoreCall = om
        ? `${scoreLower} - ${scoreUpper}`
        : dm
          ? dm.rules.formattedScoreCall()
          : solo.rules.formattedScoreCall();
      const rallyShotCount = om
        ? om.rules.rallyShotCount
        : dm
          ? dm.rules.rallyShotCount
          : solo.rules.rallyShotCount;
      const ballBouncesOnSide = om
        ? om.ball.bouncesOnCurrentSide
        : dm
          ? dm.ball.bouncesOnCurrentSide
          : solo.ball.bouncesOnCurrentSide;
      const rawBotCountdown =
        activePhase === 'WAITING_SERVE' && !om
          ? dm
            ? dm.aiServeTimerSec
            : solo.botServeDelaySec
          : 0;
      const botServeCountdownSec =
        rawBotCountdown > 0 ? Math.ceil(rawBotCountdown) : 0;
      const rallyHits = om ? om.rallyHits : dm ? dm.rallyHits : solo.rallyHits;
      const bestRally = om ? om.bestRally : dm ? dm.bestRally : solo.bestRally;
      const lastCallout = om ? om.lastCallout : dm ? dm.lastCallout : solo.lastCallout;
      const lastShotLabel = om ? om.lastShotLabel : dm ? dm.lastShotLabel : solo.lastShotLabel;
      const inReach = om ? om.canLocalReachBall() : dm ? dm.canPlayerReachBall() : solo.canPlayerReachBall();
      currentHitState = om ? 'NONE' : dm ? dm.playerHitState() : solo.playerHitState();
      const matchWinner: CourtSide | null = om
        ? om.matchWinnerLocal === true
          ? 'LOWER'
          : om.matchWinnerLocal === false
            ? 'UPPER'
            : null
        : dm
          ? dm.rules.matchWinner
          : solo.rules.matchWinner;
      const hostDisconnected = om ? om.hostDisconnected : false;

      const matchStatus =
        !om && !dm && solo.bestOf === 3
          ? `G${solo.gameNumber} · ${solo.gamesLower}-${solo.gamesUpper}`
          : '';
      const gamesLower = !om && !dm ? solo.gamesLower : 0;
      const gamesUpper = !om && !dm ? solo.gamesUpper : 0;
      const gameScoresSummary = !om && !dm && solo.bestOf === 3 ? solo.gameScores.join(', ') : '';
      const hudKey = `${activePhase}|${scoreLower}|${scoreUpper}|${servingSide}|${serverNumberCall}|${serverIndexInTeam}|${rallyShotCount}|${ballBouncesOnSide}|${botServeCountdownSec}|${rallyHits}|${bestRally}|${lastCallout}|${lastShotLabel}|${inReach}|${matchWinner}|${hostDisconnected}|${matchStatus}|${gamesLower}|${gamesUpper}|${gameScoresSummary}`;
      if (hudKey !== lastHudKey) {
        lastHudKey = hudKey;
        onHudChangeRef.current({
          rallyPhase: activePhase,
          scoreLower,
          scoreUpper,
          servingSide,
          serverNumberCall,
          serverIndexInTeam,
          formattedScoreCall,
          rallyShotCount,
          ballBouncesOnSide,
          botServeCountdownSec,
          rallyHits,
          bestRally,
          lastCallout,
          lastShotLabel,
          inReach,
          matchWinner,
          hostDisconnected,
          matchStatus,
          gamesLower,
          gamesUpper,
          gameScoresSummary,
        });
      }

      const m = getViewportMetrics();
      const dprScale = m.scale / 14;
      const shakeX = shakeIntensity > 0 ? (Math.random() * 2 - 1) * shakeIntensity * m.scale : 0;
      const shakeY = shakeIntensity > 0 ? (Math.random() * 2 - 1) * shakeIntensity * m.scale : 0;

      ctx.save();
      ctx.fillStyle = CourtColors.SURROUND_NAVY;
      ctx.fillRect(0, 0, m.cw, m.ch);
      ctx.translate(shakeX, shakeY);

      const courtTopLeft = worldToScreen(CourtSpec.COURT_LEFT_X, CourtSpec.COURT_TOP_Y, m);
      const courtW = CourtSpec.COURT_WIDTH_FT * m.scale;
      const courtH = CourtSpec.COURT_LENGTH_FT * m.scale;

      ctx.fillStyle = CourtColors.COURT_BLUE;
      ctx.fillRect(courtTopLeft.sx, courtTopLeft.sy, courtW, courtH);

      const nvzTopLeft = worldToScreen(CourtSpec.COURT_LEFT_X, CourtSpec.UPPER_NVZ_Y, m);
      const nvzH = CourtSpec.KITCHEN_DEPTH_FT * 2 * m.scale;
      ctx.fillStyle = CourtColors.KITCHEN_BLUE;
      ctx.fillRect(nvzTopLeft.sx, nvzTopLeft.sy, courtW, nvzH);

      if (activePhase === 'WAITING_SERVE') {
        const assign = om
          ? om.rules.currentServeAssignment()
          : dm
            ? dm.rules.currentServeAssignment()
            : solo.rules.currentServeAssignment();
        const localBoxTarget =
          om && !om.isHost
            ? assign.targetCourtSide === 'UPPER'
              ? 'LOWER'
              : 'UPPER'
            : assign.targetCourtSide;
        const boxSide =
          om && !om.isHost ? (assign.targetBox === 'LEFT' ? 'RIGHT' : 'LEFT') : assign.targetBox;
        const boxLeftX = boxSide === 'LEFT' ? CourtSpec.COURT_LEFT_X : CourtSpec.COURT_CENTER_X;
        const boxTopY = localBoxTarget === 'UPPER' ? CourtSpec.COURT_TOP_Y : CourtSpec.LOWER_NVZ_Y;
        const boxPt = worldToScreen(boxLeftX, boxTopY, m);
        ctx.fillStyle = 'rgba(132, 204, 22, 0.16)';
        ctx.fillRect(
          boxPt.sx,
          boxPt.sy,
          (CourtSpec.COURT_WIDTH_FT / 2) * m.scale,
          CourtSpec.SERVICE_DEPTH_FT * m.scale,
        );
      }

      const lastFault = om ? om.lastFaultReason : dm ? dm.lastFaultReason : solo.lastFaultReason;
      if (activePhase === 'POINT_PAUSE' && lastFault === 'KITCHEN_VOLLEY') {
        const lowerNvzTop = worldToScreen(CourtSpec.COURT_LEFT_X, CourtSpec.NET_Y, m);
        ctx.fillStyle = 'rgba(220, 38, 38, 0.28)';
        ctx.fillRect(
          lowerNvzTop.sx,
          lowerNvzTop.sy,
          courtW,
          CourtSpec.KITCHEN_DEPTH_FT * m.scale,
        );
      }

      const lw = Math.max(1.5, CourtSpec.LINE_WIDTH_FT * m.scale);
      const halfLw = lw / 2;
      ctx.fillStyle = CourtColors.LINE_WHITE;

      ctx.fillRect(courtTopLeft.sx - halfLw, courtTopLeft.sy - halfLw, lw, courtH + lw);
      ctx.fillRect(courtTopLeft.sx + courtW - halfLw, courtTopLeft.sy - halfLw, lw, courtH + lw);
      ctx.fillRect(courtTopLeft.sx - halfLw, courtTopLeft.sy - halfLw, courtW + lw, lw);
      ctx.fillRect(courtTopLeft.sx - halfLw, courtTopLeft.sy + courtH - halfLw, courtW + lw, lw);

      const upperNvzPt = worldToScreen(CourtSpec.COURT_LEFT_X, CourtSpec.UPPER_NVZ_Y, m);
      const lowerNvzPt = worldToScreen(CourtSpec.COURT_LEFT_X, CourtSpec.LOWER_NVZ_Y, m);
      ctx.fillRect(upperNvzPt.sx, upperNvzPt.sy - halfLw, courtW, lw);
      ctx.fillRect(lowerNvzPt.sx, lowerNvzPt.sy - halfLw, courtW, lw);

      const centerTop = worldToScreen(CourtSpec.COURT_CENTER_X, CourtSpec.COURT_TOP_Y, m);
      const centerLowerNvz = worldToScreen(CourtSpec.COURT_CENTER_X, CourtSpec.LOWER_NVZ_Y, m);
      const serviceH = CourtSpec.SERVICE_DEPTH_FT * m.scale;
      ctx.fillRect(centerTop.sx - halfLw, centerTop.sy, lw, serviceH);
      ctx.fillRect(centerLowerNvz.sx - halfLw, centerLowerNvz.sy, lw, serviceH);

      if (showDimensionsRef.current) {
        const wMid = worldToScreen(CourtSpec.COURT_CENTER_X, CourtSpec.COURT_TOP_Y + 1.8, m);
        drawDimensionTag(wMid.sx, wMid.sy, '20 ft', dprScale);
        const lMid = worldToScreen(CourtSpec.COURT_RIGHT_X + 1.7, CourtSpec.NET_Y + 6, m);
        drawDimensionTag(lMid.sx, lMid.sy, '44 ft', dprScale);
        const nvzTag = worldToScreen(CourtSpec.COURT_LEFT_X + 3.4, CourtSpec.LOWER_NVZ_Y + 3.5, m);
        drawDimensionTag(nvzTag.sx, nvzTag.sy, '7 ft NVZ', dprScale);
      }

      if (dm) {
        drawCharacter(dm.upperTeam[0], m, false);
        drawCharacter(dm.upperTeam[1], m, false);
      } else {
        const upperChar = om ? om.remotePlayer : solo.bot;
        drawCharacter(
          upperChar,
          m,
          false,
          false,
          om ? namesRef.current.room?.opponentName || namesRef.current.opponent : undefined,
          om ? namesRef.current.room?.opponentCharacter : undefined,
        );
      }

      const netLeft = worldToScreen(
        CourtSpec.COURT_LEFT_X - CourtSpec.NET_POST_OFFSET_FT,
        CourtSpec.NET_Y,
        m,
      );
      const netRight = worldToScreen(
        CourtSpec.COURT_RIGHT_X + CourtSpec.NET_POST_OFFSET_FT,
        CourtSpec.NET_Y,
        m,
      );
      const netSpan = netRight.sx - netLeft.sx;

      ctx.fillStyle = CourtColors.NET_SHADOW;
      ctx.fillRect(netLeft.sx, netLeft.sy + 0.08 * m.scale, netSpan, 0.38 * m.scale);
      ctx.fillStyle = CourtColors.NET_MESH;
      ctx.fillRect(netLeft.sx, netLeft.sy - 0.14 * m.scale, netSpan, 0.28 * m.scale);
      ctx.fillStyle = CourtColors.NET_TAPE;
      ctx.fillRect(netLeft.sx, netLeft.sy - 0.05 * m.scale, netSpan, 0.1 * m.scale);
      ctx.fillStyle = CourtColors.NET_POST;
      const postRadius = Math.max(3, 0.28 * m.scale);
      [netLeft.sx, netRight.sx].forEach((px) => {
        ctx.beginPath();
        ctx.arc(px, netLeft.sy, postRadius, 0, Math.PI * 2);
        ctx.fill();
      });

      if (dm) {
        drawCharacter(dm.partner, m, false, false);
        drawCharacter(dm.player, m, inReach, true, namesRef.current.player);
      } else {
        const lowerChar = om ? om.localPlayer : solo.player;
        drawCharacter(lowerChar, m, inReach, true, namesRef.current.player);
      }

      if (pointerActive && !swipeConsumed) {
        const s0 = worldToScreen(swipeStartX, swipeStartY, m);
        const s1 = worldToScreen(swipeCurX, swipeCurY, m);
        if (Math.hypot(swipeCurX - swipeStartX, swipeCurY - swipeStartY) > 0.35) {
          ctx.save();
          ctx.strokeStyle = 'rgba(132, 204, 22, 0.72)';
          ctx.lineWidth = Math.max(2, 0.12 * m.scale);
          ctx.lineCap = 'round';
          ctx.beginPath();
          ctx.moveTo(s0.sx, s0.sy);
          ctx.lineTo(s1.sx, s1.sy);
          ctx.stroke();
          ctx.restore();
        }
      }

      for (let i = 0; i < particles.length; i++) {
        const p = particles[i];
        if (p.life > 0) {
          const sp = worldToScreen(p.x, p.y, m);
          ctx.save();
          ctx.globalAlpha = Math.max(0, p.life / p.maxLife);
          ctx.fillStyle = p.color;
          ctx.beginPath();
          ctx.arc(sp.sx, sp.sy, Math.max(1.5, 0.13 * m.scale), 0, Math.PI * 2);
          ctx.fill();
          ctx.restore();
        }
      }

      const ball = om ? om.ball : dm ? dm.ball : solo.ball;
      const groundPt = worldToScreen(ball.x, ball.y, m);
      const visualBallPt = worldToScreen(ball.x, ball.y + ball.z * 0.32, m);
      const scale = spriteScaleForHeight(ball.z);
      const ballRadiusPx = Math.max(3, BallPhysicsConstants.BALL_RADIUS_FT * scale * m.scale);

      if (ball.z > 2.2) {
        ctx.save();
        ctx.strokeStyle = 'rgba(250, 204, 21, 0.18)';
        ctx.lineWidth = Math.max(1, 0.05 * m.scale);
        ctx.setLineDash([0.16 * m.scale, 0.18 * m.scale]);
        ctx.beginPath();
        ctx.moveTo(groundPt.sx, groundPt.sy);
        ctx.lineTo(visualBallPt.sx, visualBallPt.sy);
        ctx.stroke();
        ctx.restore();
      }

      const shadowAlpha = Math.max(0.14, Math.min(0.45, 0.44 - ball.z * 0.025));
      const shadowRadiusPx = Math.max(
        2.5,
        BallPhysicsConstants.BALL_RADIUS_FT * (1 + ball.z * 0.04) * m.scale,
      );
      ctx.fillStyle = `rgba(8, 12, 24, ${shadowAlpha})`;
      ctx.beginPath();
      ctx.arc(groundPt.sx, groundPt.sy, shadowRadiusPx, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = '#FACC15';
      ctx.beginPath();
      ctx.arc(visualBallPt.sx, visualBallPt.sy, ballRadiusPx, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#1E293B';
      ctx.lineWidth = Math.max(1.2, 0.05 * m.scale);
      ctx.stroke();

      ctx.fillStyle = 'rgba(161, 98, 7, 0.55)';
      ctx.beginPath();
      ctx.arc(
        visualBallPt.sx - ballRadiusPx * 0.28,
        visualBallPt.sy - ballRadiusPx * 0.22,
        ballRadiusPx * 0.2,
        0,
        Math.PI * 2,
      );
      ctx.fill();

      if (calloutAlpha > 0.02 && lastCallout) {
        ctx.save();
        ctx.globalAlpha = Math.min(1, calloutAlpha * 1.25);
        const callPt = worldToScreen(CourtSpec.COURT_CENTER_X, CourtSpec.NET_Y + 0.5, m);
        const fontSize = Math.round(13 * dprScale);
        ctx.font = `800 ${fontSize}px "Bricolage Grotesque", system-ui, sans-serif`;
        const tw = ctx.measureText(lastCallout).width;
        const boxW = tw + 18 * dprScale;
        const boxH = 26 * dprScale;
        const isFault =
          lastCallout.includes('fault') ||
          lastCallout.includes('Net!') ||
          lastCallout.includes('Out!') ||
          lastCallout.includes('Double bounce!');

        ctx.fillStyle = isFault ? 'rgba(220, 38, 38, 0.94)' : 'rgba(11, 18, 32, 0.9)';
        ctx.beginPath();
        ctx.roundRect(callPt.sx - boxW / 2, callPt.sy - boxH / 2, boxW, boxH, 8 * dprScale);
        ctx.fill();
        ctx.strokeStyle = isFault ? 'rgba(254, 202, 202, 0.55)' : 'rgba(132, 204, 22, 0.65)';
        ctx.lineWidth = Math.max(1, dprScale);
        ctx.stroke();

        ctx.fillStyle = '#FFFFFF';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(lastCallout, callPt.sx, callPt.sy + 0.5 * dprScale);
        ctx.restore();
      }

      ctx.restore();
      rafId = requestAnimationFrame(renderFrame);
    };

    rafId = requestAnimationFrame(renderFrame);

    return () => {
      cancelAnimationFrame(rafId);
      resizeObserver.disconnect();
      canvas.removeEventListener('pointerdown', handlePointerDown);
      canvas.removeEventListener('pointermove', handlePointerMove);
      canvas.removeEventListener('pointerup', handlePointerUp);
      canvas.removeEventListener('pointercancel', handlePointerUp);
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
      window.removeEventListener('blur', handleBlur);
    };
  }, []);

  return (
    <div ref={containerRef} className="relative h-full w-full min-h-0 select-none touch-none">
      <canvas ref={canvasRef} className="block h-full w-full cursor-pointer" />
    </div>
  );
}
