import { signOut } from 'firebase/auth';
import { auth as firebaseAuth } from '../game/firebaseClient';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  BarChart3,
  BookOpen,
  Bot,
  Check,
  Copy,
  Lock,
  LogIn,
  LogOut,
  Maximize2,
  Pause,
  Play,
  RotateCcw,
  Ruler,
  Trophy,
  Unlock,
  Users,
  Wifi,
  SlidersHorizontal,
  Volume2,
  VolumeX,
} from 'lucide-react';
import type { User } from 'firebase/auth';
import {
  BOT_PROFILES,
  CHARACTER_PALETTES,
  matchFormat,
  type BotDifficulty,
  type CharacterId,
} from '../game/ballPhysics';
import {
  ensureUserProfile,
  recordFinishedMatch,
  rtdb,
  saveUserCharacter,
  signInPlayerAnonymously,
  signInPlayerWithGoogle,
  signOutPlayer,
  subscribeAuthUser,
  testConnection,
} from '../game/firebaseClient';
import { RoomCodeGenerator } from '../game/onlineMatch1v1';
import { OnlineRoomSession, NearbyLobby, type NearbyHostInfo } from '../game/onlineRoomService';
import type { ScoringMode } from '../game/rulesEngine';
import { soundFx } from '../game/soundFx';
import { CharacterPortrait } from './CharacterPortrait';
import { CourtCanvas, type HudSnapshot } from './CourtCanvas';
import { StatsHubView } from './StatsHubView';
import { LearnView } from './LearnView';

type GameState =
  | 'TITLE_MENU'
  | 'CHARACTER_SELECT'
  | 'STATS_HUB'
  | 'LEARN'
  | 'PLAYING'
  | 'PAUSED'
  | 'GAME_OVER';
type TitleTab = 'SOLO' | 'DOUBLES' | 'ONLINE' | 'NEARBY';

/** Online 1v1 is paused for now; the tab shows a "coming soon" note. Set true to bring it back. */
const ONLINE_ENABLED = false;

const ROSTER_ORDER: CharacterId[] = [
  'RIO',
  'LOLA',
  'BERT',
  'DILL',
  'MAYA',
  'KAI',
  'ZOE',
  'GUS',
  'NINA',
  'DUKE',
];
const CHAR_STORAGE_KEY = 'pickle-rally-selected-character';
const CONTROLS_HINT_STORAGE_KEY = 'pickle-rally-controls-hint-seen-v1';

export function PhoneStage() {
  const [gameState, setGameState] = useState<GameState>('TITLE_MENU');
  const [titleTab, setTitleTab] = useState<TitleTab>('SOLO');
  const [scoringMode, setScoringMode] = useState<ScoringMode>('RALLY');
  const [pointsToWin, setPointsToWin] = useState<11 | 15 | 21>(matchFormat.pointsToWin);
  const [bestOf, setBestOf] = useState<1 | 3>(matchFormat.bestOf);
  const [difficulty, setDifficulty] = useState<BotDifficulty>(matchFormat.difficulty);
  const [showMatchSettings, setShowMatchSettings] = useState(false);
  // Loading screen shown when the app opens: ~2.2s, then a short fade. Tap to skip.
  const [showSplash, setShowSplash] = useState(true);
  const [splashFading, setSplashFading] = useState(false);
  const [splashFill, setSplashFill] = useState(false);
  useEffect(() => {
    const fill = setTimeout(() => setSplashFill(true), 60);
    const fade = setTimeout(() => setSplashFading(true), 2200);
    const hide = setTimeout(() => setShowSplash(false), 2700);
    return () => {
      clearTimeout(fill);
      clearTimeout(fade);
      clearTimeout(hide);
    };
  }, []);
  const [showDimensions, setShowDimensions] = useState(false);
  const [muted, setMuted] = useState(false);
  const [serveNonce, setServeNonce] = useState(0);
  const [receiveNonce, setReceiveNonce] = useState(0);
  const [resetNonce, setResetNonce] = useState(0);
  const [hasSeenControlsHint, setHasSeenControlsHint] = useState<boolean>(() => {
    if (typeof window === 'undefined') return true;
    return window.localStorage.getItem(CONTROLS_HINT_STORAGE_KEY) === '1';
  });

  // Phase 5 & 6 Character Select state
  const [playerChar, setPlayerChar] = useState<CharacterId>(() => {
    if (typeof window === 'undefined') return 'RIO';
    const saved = window.localStorage.getItem(CHAR_STORAGE_KEY);
    return saved && saved in CHARACTER_PALETTES ? (saved as CharacterId) : 'RIO';
  });
  const [partnerChar, setPartnerChar] = useState<CharacterId>('DILL');
  const [opponentChar, setOpponentChar] = useState<CharacterId>('LOLA');
  const [dillUnlocked, setDillUnlocked] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false;
    return window.localStorage.getItem(CHAR_STORAGE_KEY) === 'DILL';
  });
  const [matchSaveState, setMatchSaveState] = useState<'IDLE' | 'SAVING' | 'CLOUD' | 'LOCAL'>('IDLE');
  const savedMatchGuardRef = useRef(false);

  // Firebase Auth + Phase 4 1v1 Room state
  const [authUser, setAuthUser] = useState<User | null>(null);
  const [authBusy, setAuthBusy] = useState(false);
  const [roomInput, setRoomInput] = useState('');
  // Profile name: shown above your player and on the scoreboard. Your hero keeps its own look.
  const [profileName, setProfileName] = useState<string>(() => {
    try {
      return localStorage.getItem('pickleRally.profileName') ?? '';
    } catch {
      return '';
    }
  });
  const [editingName, setEditingName] = useState<boolean>(() => {
    try {
      return !localStorage.getItem('pickleRally.profileName');
    } catch {
      return false;
    }
  });
  const [nameDraft, setNameDraft] = useState('');
  const playerDisplayName =
    profileName.trim() ||
    (authUser && !authUser.isAnonymous && authUser.displayName ? authUser.displayName.split(' ')[0] : '');
  const saveProfileName = (raw: string) => {
    const clean = raw.replace(/\s+/g, ' ').trim().slice(0, 14);
    if (!clean) {
      setEditingName(false);
      return;
    }
    setProfileName(clean);
    try {
      localStorage.setItem('pickleRally.profileName', clean);
    } catch {
      // storage unavailable (private mode): keep it for this session only
    }
    setEditingName(false);
  };
  const [roomError, setRoomError] = useState<string | null>(null);
  const [roomBusy, setRoomBusy] = useState(false);
  const [onlineSession, setOnlineSession] = useState<OnlineRoomSession | null>(null);
  const [nearbyHosts, setNearbyHosts] = useState<NearbyHostInfo[]>([]);
  const [opponentConnected, setOpponentConnected] = useState(false);
  const [simGuestActive, setSimGuestActive] = useState(false);
  const [copiedRoomCode, setCopiedRoomCode] = useState(false);

  const [hud, setHud] = useState<HudSnapshot>({
    rallyPhase: 'WAITING_SERVE',
    scoreLower: 0,
    scoreUpper: 0,
    servingSide: 'LOWER',
    serverNumberCall: 1,
    serverIndexInTeam: 0,
    formattedScoreCall: '0 - 0',
    rallyShotCount: 0,
    ballBouncesOnSide: 0,
    botServeCountdownSec: 0,
    rallyHits: 0,
    bestRally: 0,
    lastCallout: 'Your serve (0 - 0)',
    lastShotLabel: '',
    inReach: false,
    matchWinner: null,
    hostDisconnected: false,
    matchStatus: '',
    gamesLower: 0,
    gamesUpper: 0,
    gameScoresSummary: '',
  });

  const markControlsHintSeen = useCallback(() => {
    setHasSeenControlsHint((prev) => {
      if (prev) return prev;
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(CONTROLS_HINT_STORAGE_KEY, '1');
      }
      return true;
    });
  }, []);

  useEffect(() => {
    const unsub = subscribeAuthUser((user) => {
      setAuthUser(user);
      if (user) {
        void testConnection();
        void ensureUserProfile(user, playerChar)
          .then((savedChar) => {
            setPlayerChar(savedChar);
            if (savedChar === 'DILL') setDillUnlocked(true);
            if (opponentChar === savedChar) {
              setOpponentChar(savedChar === 'LOLA' ? 'RIO' : 'LOLA');
            }
          })
          .catch(() => {});
      }
    });
    return unsub;
    // Subscribe once on mount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSelectCharacter = useCallback(
    (id: CharacterId) => {
      if (CHARACTER_PALETTES[id].isUnlockable && !dillUnlocked) {
        return;
      }
      soundFx.playMenuClick();
      setPlayerChar(id);
      if (opponentChar === id) {
        setOpponentChar(id === 'LOLA' ? 'RIO' : 'LOLA');
      }
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(CHAR_STORAGE_KEY, id);
      }
      if (authUser) {
        void saveUserCharacter(authUser, id).catch(() => {});
      }
    },
    [authUser, dillUnlocked, opponentChar],
  );

  const handleToggleDillUnlock = () => {
    soundFx.playMenuClick();
    setDillUnlocked((prev) => {
      const next = !prev;
      if (next) {
        setPlayerChar('DILL');
        if (opponentChar === 'DILL') setOpponentChar('LOLA');
        if (typeof window !== 'undefined') {
          window.localStorage.setItem(CHAR_STORAGE_KEY, 'DILL');
        }
        if (authUser) {
          void saveUserCharacter(authUser, 'DILL').catch(() => {});
        }
      } else if (playerChar === 'DILL') {
        setPlayerChar('RIO');
      }
      return next;
    });
  };

  const handleHudChange = useCallback(
    (next: HudSnapshot) => {
      setHud(next);
      if (next.rallyPhase === 'RALLY_LIVE' || next.scoreLower > 0 || next.scoreUpper > 0) {
        markControlsHintSeen();
      }
      if (next.matchWinner !== null || next.hostDisconnected) {
        setGameState('GAME_OVER');
        if (next.matchWinner !== null && !savedMatchGuardRef.current) {
          savedMatchGuardRef.current = true;
          setMatchSaveState('SAVING');
          const isOnlineRanked = Boolean(onlineSession && !onlineSession.room.isNearby);
          const mode = isOnlineRanked
            ? 'SINGLES_1V1'
            : titleTab === 'DOUBLES'
              ? 'SOLO_2V2'
              : 'SOLO_1V1';
          void recordFinishedMatch(onlineSession?.room.isNearby ? null : authUser, {
            roomCode: isOnlineRanked ? onlineSession?.room.roomCode : undefined,
            mode,
            scoringMode,
            playerCharacter: playerChar,
            partnerCharacter: mode === 'SOLO_2V2' ? partnerChar : undefined,
            opponentCharacter: opponentChar,
            winnerSide: next.matchWinner,
            scoreLower: next.scoreLower,
            scoreUpper: next.scoreUpper,
            bestRally: next.bestRally,
          })
            .then((res) => {
              setMatchSaveState(res.savedToCloud ? 'CLOUD' : 'LOCAL');
            })
            .catch(() => {
              setMatchSaveState('LOCAL');
            });
        }
      }
    },
    [authUser, onlineSession, opponentChar, partnerChar, playerChar, scoringMode, titleTab],
  );

  const handleEnterSoloCourt = useCallback(async () => {
    if (onlineSession) {
      await onlineSession.leave();
      setOnlineSession(null);
    }
    savedMatchGuardRef.current = false;
    setMatchSaveState('IDLE');
    setSimGuestActive(false);
    setOpponentConnected(false);
    soundFx.playEnterCourt();
    setResetNonce((n) => n + 1);
    setGameState('PLAYING');
  }, [onlineSession]);

  const handleGoogleSignIn = async () => {
    setAuthBusy(true);
    setRoomError(null);
    try {
      await signInPlayerWithGoogle();
    } catch (err) {
      setRoomError(err instanceof Error ? err.message : 'Google sign-in canceled');
    } finally {
      setAuthBusy(false);
    }
  };

  const handleAnonSignIn = async () => {
    setAuthBusy(true);
    setRoomError(null);
    try {
      await signInPlayerAnonymously();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Anonymous sign-in failed';
      setRoomError(
        msg.includes('admin-restricted-operation') || msg.includes('operation-not-allowed')
          ? 'Enable Anonymous Auth in Firebase Console (local tab rooms still work)'
          : msg,
      );
    } finally {
      setAuthBusy(false);
    }
  };

  type PickleBridgeWindow = Window & {
    __pickleOnlineBridge?: {
      onRemoteInput: (inp: import('../game/onlineMatch1v1').PlayerInputSnapshot) => void;
      onHostSnapshot: (snap: import('../game/onlineMatch1v1').MatchStateSnapshot) => void;
      onRoomStatusChange: (status: import('../game/onlineMatch1v1').RoomStatus) => void;
    };
  };

  const getBridge = () => (window as PickleBridgeWindow).__pickleOnlineBridge;

  const bridgeCallbacks = {
    onOpponentJoined: () => {
      setOpponentConnected(true);
    },
    onRemoteInput: (inp: import('../game/onlineMatch1v1').PlayerInputSnapshot) => {
      getBridge()?.onRemoteInput(inp);
    },
    onHostSnapshot: (snap: import('../game/onlineMatch1v1').MatchStateSnapshot) => {
      getBridge()?.onHostSnapshot(snap);
    },
    onRoomStatusChange: (status: import('../game/onlineMatch1v1').RoomStatus) => {
      getBridge()?.onRoomStatusChange(status);
    },
  };

  // Nearby: search for hosts while the Nearby tab is open on the title screen.
  useEffect(() => {
    if (gameState !== 'TITLE_MENU' || titleTab !== 'NEARBY') {
      setNearbyHosts([]);
      return;
    }
    return NearbyLobby.discover(setNearbyHosts);
  }, [gameState, titleTab]);

  // Nearby: announce our hosted game until someone joins.
  useEffect(() => {
    const room = onlineSession?.room;
    if (!room || !room.isHost || room.transport !== 'LOCAL_TAB_SYNC' || opponentConnected) return;
    return NearbyLobby.announce(room.roomCode, (playerDisplayName || CHARACTER_PALETTES[playerChar].name), playerChar);
  }, [onlineSession, opponentConnected, playerChar]);

  const handleCreateRoom = () => startHostRoom(false);
  const startHostRoom = async (local: boolean) => {
    setRoomBusy(true);
    setRoomError(null);
    try {
      if (onlineSession) await onlineSession.leave();
      savedMatchGuardRef.current = false;
      setMatchSaveState('IDLE');
      const session = local
        ? await OnlineRoomSession.createLocalRoom(bridgeCallbacks, playerChar, playerDisplayName)
        : await OnlineRoomSession.createRoom(bridgeCallbacks, playerChar);
      setOnlineSession(session);
      setOpponentConnected(false);
      setSimGuestActive(false);
      soundFx.playEnterCourt();
      setGameState('PLAYING');
    } catch (err) {
      setRoomError(err instanceof Error ? err.message : 'Could not create room');
    } finally {
      setRoomBusy(false);
    }
  };

  const handleJoinRoom = () => startJoinRoom(roomInput, false);
  const startJoinRoom = async (code: string, local: boolean) => {
    const normalized = code.trim().toUpperCase();
    if (!RoomCodeGenerator.isValid(normalized)) {
      setRoomError('Enter room code like PKL-482');
      return;
    }
    setRoomBusy(true);
    setRoomError(null);
    try {
      if (onlineSession) await onlineSession.leave();
      savedMatchGuardRef.current = false;
      setMatchSaveState('IDLE');
      const session = local
        ? await OnlineRoomSession.joinLocalRoom(normalized, bridgeCallbacks, playerChar, playerDisplayName)
        : await OnlineRoomSession.joinRoom(normalized, bridgeCallbacks, playerChar);
      setOnlineSession(session);
      setOpponentConnected(true);
      setSimGuestActive(false);
      soundFx.playEnterCourt();
      setGameState('PLAYING');
    } catch (err) {
      setRoomError(err instanceof Error ? err.message : 'Could not join room');
    } finally {
      setRoomBusy(false);
    }
  };

  const handleLeaveOnlineRoom = useCallback(async () => {
    if (onlineSession) {
      await onlineSession.leave();
      setOnlineSession(null);
    }
    setOpponentConnected(false);
    setSimGuestActive(false);
    setGameState('TITLE_MENU');
  }, [onlineSession]);

  const handleCopyRoomCode = async () => {
    if (!onlineSession) return;
    try {
      await navigator.clipboard.writeText(onlineSession.room.roomCode);
      setCopiedRoomCode(true);
      setTimeout(() => setCopiedRoomCode(false), 1400);
    } catch {
      // Ignore clipboard failures in restricted frames
    }
  };

  const toggleMute = useCallback(() => {
    setMuted((prev) => {
      const next = !prev;
      soundFx.setMuted(next);
      return next;
    });
  }, []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) {
        return;
      }
      if (e.key === 'Enter' || e.key === ' ') {
        if (gameState === 'TITLE_MENU' && (titleTab === 'SOLO' || titleTab === 'DOUBLES')) {
          e.preventDefault();
          void handleEnterSoloCourt();
        }
      } else if (e.key === 'Escape') {
        if (gameState === 'PLAYING') {
          e.preventDefault();
          setGameState('PAUSED');
        } else if (gameState === 'PAUSED') {
          e.preventDefault();
          setGameState('PLAYING');
        } else if (gameState === 'CHARACTER_SELECT' || gameState === 'STATS_HUB' || gameState === 'LEARN') {
          e.preventDefault();
          setGameState('TITLE_MENU');
        }
      } else if (e.key.toLowerCase() === 'm') {
        toggleMute();
      } else if (e.key.toLowerCase() === 'd' && gameState === 'PLAYING') {
        setShowDimensions((prev) => !prev);
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [gameState, titleTab, handleEnterSoloCourt, toggleMute]);

  const hasNetworkSession = onlineSession !== null;
  const isNearby = Boolean(onlineSession?.room.isNearby);
  const isOnline = hasNetworkSession && !isNearby;
  const isDoubles = !hasNetworkSession && titleTab === 'DOUBLES';
  const playerPalette = CHARACTER_PALETTES[playerChar];
  const partnerPalette = CHARACTER_PALETTES[partnerChar];
  const opponentPalette = CHARACTER_PALETTES[opponentChar];

  const localLabel = hasNetworkSession
    ? playerDisplayName || (onlineSession.room.isHost
      ? 'Host'
      : 'Guest')
    : isDoubles
      ? 'Your Team'
      : playerDisplayName || playerPalette.name;
  // Short scoreboard names so the 2v2 score line fits next to the buttons.
  const scoreLocalLabel = isDoubles && !hasNetworkSession ? 'Us' : null;
  const scoreRemoteLabel = isDoubles && !hasNetworkSession ? 'Them' : null;
  const remoteLabel = hasNetworkSession
    ? onlineSession.room.opponentName || (onlineSession.room.isHost
      ? 'Guest'
      : 'Host')
    : isDoubles
      ? 'Opponents'
      : opponentPalette.name;

  const isFaultCallout =
    hud.lastCallout.includes('fault') ||
    hud.lastCallout.includes('Out!') ||
    hud.lastCallout.includes('Net!') ||
    hud.lastCallout.includes('Double bounce!');

  const rulePhaseLabel = hasNetworkSession
    ? hud.hostDisconnected
      ? 'Host disconnected'
      : isNearby
        ? !opponentConnected && onlineSession.room.isHost
          ? 'Waiting for nearby player…'
          : hud.lastShotLabel || 'Nearby 1v1'
        : !opponentConnected && !simGuestActive && onlineSession.room.isHost
          ? `Share ${onlineSession.room.roomCode} or Sim Guest`
          : hud.lastShotLabel || `${onlineSession.room.roomCode} · ${rtdb ? 'RTDB' : 'Local Sync'}`
    : hud.rallyPhase === 'POINT_PAUSE'
      ? hud.lastCallout || 'Next point'
      : hud.rallyPhase === 'WAITING_SERVE'
        ? hud.servingSide === 'LOWER' && isDoubles && hud.serverIndexInTeam !== 0
          ? `${partnerPalette.name} serving…${hud.botServeCountdownSec > 0 ? ` ${hud.botServeCountdownSec}s` : ''}`
          : hud.servingSide === 'LOWER'
          ? `Serve diagonally · ${scoringMode === 'RALLY' ? 'Rally' : 'Side-Out'} to ${titleTab === 'SOLO' ? pointsToWin : 11}`
          : `${remoteLabel} serving…${hud.botServeCountdownSec > 0 ? ` ${hud.botServeCountdownSec}s` : ''}`
        : hud.rallyShotCount === 1
          ? hud.ballBouncesOnSide === 0
            ? 'Return must bounce'
            : 'Serve bounced · Hit return'
          : hud.rallyShotCount === 2
            ? '3rd shot must bounce'
            : hud.inReach
              ? 'In reach · Swipe!'
              : isDoubles
                ? `2v2 · ${hud.formattedScoreCall}`
                : hud.lastShotLabel || 'Open rally';

  return (
    <div className="flex flex-col items-center justify-center">
      {/* Portrait Phone Frame mirroring DesktopLauncher (450x900, 1:2 FitViewport) */}
      <div className="relative flex aspect-[1/2] h-[min(86vh,760px)] w-auto max-w-full flex-col overflow-hidden rounded-[28px] border border-slate-700/80 bg-[#15223B] shadow-2xl max-sm:aspect-auto max-sm:h-[100dvh] max-sm:w-screen max-sm:max-w-none max-sm:rounded-none max-sm:border-0 max-sm:shadow-none">
        {/* Loading screen (app open) */}
        {showSplash && (
          <button
            type="button"
            onClick={() => setShowSplash(false)}
            aria-label="Skip loading screen"
            className={`absolute inset-0 z-50 flex items-center justify-center bg-[#14213D] transition-opacity duration-500 cursor-pointer ${
              splashFading ? 'opacity-0' : 'opacity-100'
            }`}
          >
            {/* Image box keeps the art's 780x1688 ratio so the animated bar lines up with it. */}
            <div className="relative aspect-[780/1688] h-full max-w-full">
              <img
                src="/assets/aistudio/Loading_screen_2x_f477c86d.png"
                alt="Pickle Rally loading screen"
                referrerPolicy="no-referrer"
                className="absolute inset-0 h-full w-full"
              />
              {/* Covers the static bar inside the art and draws one that fills up block by block. */}
              <div
                className="absolute bg-[#0A1222]"
                style={{ left: '14.36%', top: '77.31%', width: '71.41%', height: '2.9%' }}
              >
                <div
                  className="absolute"
                  style={{
                    left: '1.44%',
                    width: '95.51%',
                    top: '18.4%',
                    height: '63.3%',
                    backgroundImage: 'linear-gradient(90deg, #7FB241 0 77.8%, transparent 77.8% 100%)',
                    backgroundSize: '6.767% 100%',
                    backgroundRepeat: 'repeat-x',
                    clipPath: splashFill ? 'inset(0 0 0 0)' : 'inset(0 100% 0 0)',
                    transition: 'clip-path 2.1s steps(15, end)',
                  }}
                />
              </div>
            </div>
          </button>
        )}

        {/* Title Menu */}
        {gameState === 'TITLE_MENU' && (
          <div className="relative flex h-full w-full flex-col justify-between overflow-hidden bg-[#15223B]">
            <img
              src="/assets/aistudio/Loading_screen_2x_f477c86d.png"
              alt="Pickle Rally pixel art splash screen"
              referrerPolicy="no-referrer"
              className="absolute inset-0 h-full w-full object-cover object-center opacity-28"
            />
            <div className="absolute inset-0 bg-gradient-to-b from-[#0E172A]/88 via-[#15223B]/65 to-[#0E172A]/96" />

            {/* Top Stats & Audio Controls */}
            <div className="relative z-10 flex items-center justify-between px-4 pt-3.5">
              <button
                type="button"
                onClick={() => {
                  soundFx.playMenuClick();
                  setGameState('STATS_HUB');
                }}
                className="flex h-9 items-center gap-1.5 rounded-lg border border-white/15 bg-[#0B1220]/85 px-2.5 text-xs font-semibold text-slate-200 transition-colors duration-150 hover:bg-[#1E293B] whitespace-nowrap shrink-0 cursor-pointer"
              >
                <BarChart3 className="h-3.5 w-3.5 text-[#84CC16]" />
                <span>Stats &amp; Ranks</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  soundFx.playMenuClick();
                  setGameState('LEARN');
                }}
                className="flex h-9 items-center gap-1.5 rounded-lg border border-[#84CC16]/40 bg-[#0B1220]/85 px-2.5 text-xs font-semibold text-slate-200 transition-colors duration-150 hover:bg-[#1E293B] whitespace-nowrap shrink-0 cursor-pointer"
              >
                <BookOpen className="h-3.5 w-3.5 text-[#84CC16]" />
                <span>Learn</span>
              </button>
              <button
                type="button"
                onClick={toggleMute}
                aria-label={muted ? 'Unmute audio' : 'Mute audio'}
                title="Mute / Unmute (M)"
                className="flex h-9 w-9 items-center justify-center rounded-lg border border-white/15 bg-[#0B1220]/85 text-slate-200 transition-colors duration-150 hover:bg-[#1E293B] cursor-pointer"
              >
                {muted ? <VolumeX className="h-4 w-4" /> : <Volume2 className="h-4 w-4" />}
              </button>
            </div>

            {/* Brand Title */}
            <div className="relative z-10 flex flex-col items-center px-6 text-center">
              <svg
                viewBox="0 0 280 86"
                className="w-52 max-w-full drop-shadow-[0_4px_12px_rgba(0,0,0,0.55)]"
                aria-label="Pickle Rally"
              >
                <text
                  x="140"
                  y="36"
                  textAnchor="middle"
                  fill="#84CC16"
                  stroke="#0B1220"
                  strokeWidth="6"
                  paintOrder="stroke"
                  fontFamily="Bricolage Grotesque, system-ui, sans-serif"
                  fontWeight="800"
                  fontSize="36"
                  letterSpacing="2"
                >
                  PICKLE
                </text>
                <text
                  x="140"
                  y="74"
                  textAnchor="middle"
                  fill="#F97316"
                  stroke="#0B1220"
                  strokeWidth="6"
                  paintOrder="stroke"
                  fontFamily="Bricolage Grotesque, system-ui, sans-serif"
                  fontWeight="800"
                  fontSize="36"
                  letterSpacing="2"
                >
                  RALLY
                </text>
              </svg>

              {/* Selected Character Strip */}
              <div className="mt-3 flex w-full items-center justify-between gap-3 rounded-xl border border-white/10 bg-[#0B1220]/90 p-2.5 text-left">
                <div className="flex items-center gap-2.5 min-w-0">
                  <CharacterPortrait characterId={playerChar} size={46} />
                  <div className="min-w-0">
                    <div className="text-xs font-bold text-white truncate">
                      {editingName ? (
                        <form
                          onSubmit={(e) => {
                            e.preventDefault();
                            saveProfileName(nameDraft);
                          }}
                          className="flex items-center gap-1"
                        >
                          <input
                            autoFocus
                            value={nameDraft}
                            maxLength={14}
                            onChange={(e) => setNameDraft(e.target.value)}
                            onBlur={() => saveProfileName(nameDraft)}
                            placeholder="Your profile name"
                            aria-label="Profile name"
                            className="w-32 rounded border border-slate-600 bg-[#121C30] px-1.5 py-0.5 text-xs font-semibold text-white outline-none focus:border-lime-400"
                          />
                        </form>
                      ) : (
                        <button
                          type="button"
                          onClick={() => {
                            setNameDraft(playerDisplayName);
                            setEditingName(true);
                          }}
                          title="Tap to change your profile name"
                          className="flex max-w-full cursor-pointer items-center gap-1 text-left"
                        >
                          <span className={`truncate ${playerDisplayName ? '' : 'text-lime-300'}`}>
                            {playerDisplayName || 'Set your name'}
                          </span>
                          <span className="shrink-0 text-[10px] font-normal text-lime-300">✎</span>
                          <span className="shrink-0 text-[10px] font-normal text-slate-400">· {playerPalette.name}</span>
                        </button>
                      )}

                    </div>
                    <div className="text-[11px] text-slate-400 truncate">
                      {playerPalette.tagline} · {playerPalette.description}
                    </div>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    soundFx.playMenuClick();
                    setGameState('CHARACTER_SELECT');
                  }}
                  className="rounded-lg border border-slate-700 bg-[#1E293B] px-3 py-2 text-[11px] font-semibold text-white transition-colors hover:bg-slate-700 whitespace-nowrap shrink-0 cursor-pointer"
                >
                  Roster
                </button>
              </div>
            </div>

            {/* Mode & Play Controls */}
            <div className="relative z-10 flex flex-col gap-2.5 px-5 pb-6">
              {/* Account: Google sign-in for cloud stats & rankings */}
              {authUser && !authUser.isAnonymous ? (
                <div className="flex w-full items-center justify-between gap-2 rounded-xl border border-white/10 bg-[#0B1220]/90 px-3 py-2 text-[11px]">
                  <span className="truncate text-slate-300">
                    Signed in as{' '}
                    <span className="font-semibold text-white">
                      {authUser.displayName || authUser.email || 'Player'}
                    </span>
                  </span>
                  <button
                    type="button"
                    onClick={() => void signOut(firebaseAuth)}
                    className="shrink-0 cursor-pointer text-slate-400 hover:text-white"
                  >
                    Sign out
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  disabled={authBusy}
                  onClick={() => void handleGoogleSignIn()}
                  className="flex w-full items-center justify-center gap-2 rounded-xl border border-white/10 bg-[#0B1220]/90 px-3 py-2 text-[11px] font-semibold text-white hover:bg-slate-800 disabled:opacity-50 cursor-pointer"
                >
                  <LogIn className="h-3.5 w-3.5" />
                  <span>{authBusy ? 'Signing in…' : 'Sign in with Google to save stats & rankings'}</span>
                </button>
              )}
              {roomError && (
                <div className="text-center text-[11px] text-rose-400">{roomError}</div>
              )}
              <div className="grid w-full grid-cols-4 gap-1 rounded-xl border border-white/10 bg-[#0B1220]/90 p-1">
                <button
                  type="button"
                  onClick={() => {
                    soundFx.playMenuClick();
                    setTitleTab('SOLO');
                    setRoomError(null);
                  }}
                  className={`flex items-center justify-center gap-1 rounded-lg py-2 text-[11px] font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                    titleTab === 'SOLO'
                      ? 'bg-[#1E293B] text-white'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Bot className="h-3.5 w-3.5" />
                  <span>Solo</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    soundFx.playMenuClick();
                    setTitleTab('DOUBLES');
                    setRoomError(null);
                  }}
                  className={`flex items-center justify-center gap-1 rounded-lg py-2 text-[11px] font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                    titleTab === 'DOUBLES'
                      ? 'bg-[#1E293B] text-[#84CC16]'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Users className="h-3.5 w-3.5" />
                  <span>2v2</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    soundFx.playMenuClick();
                    setTitleTab('ONLINE');
                    setRoomError(null);
                  }}
                  className={`flex items-center justify-center gap-1 rounded-lg py-2 text-[11px] font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                    titleTab === 'ONLINE'
                      ? 'bg-[#1E293B] text-[#84CC16]'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Users className="h-3.5 w-3.5" />
                  <span>Online</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    soundFx.playMenuClick();
                    setTitleTab('NEARBY');
                    setRoomError(null);
                  }}
                  className={`flex items-center justify-center gap-1 rounded-lg py-2 text-[11px] font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                    titleTab === 'NEARBY'
                      ? 'bg-[#1E293B] text-[#84CC16]'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Wifi className="h-3.5 w-3.5" />
                  <span>Nearby</span>
                </button>
              </div>

              {titleTab === 'SOLO' || titleTab === 'DOUBLES' ? (
                <>
                  <button
                    type="button"
                    onClick={() => {
                      soundFx.playMenuClick();
                      setShowMatchSettings(true);
                    }}
                    className="flex w-full items-center justify-between gap-2 rounded-xl border border-white/10 bg-[#0B1220]/85 px-3 py-2.5 text-left transition-colors hover:border-[#84CC16]/60 cursor-pointer"
                  >
                    <span className="flex shrink-0 items-center gap-1.5 text-xs font-semibold text-slate-200">
                      <SlidersHorizontal className="h-3.5 w-3.5 text-[#84CC16]" />
                      Match settings
                    </span>
                    <span className="min-w-0 truncate text-[11px] text-slate-400">
                      {`${scoringMode === 'RALLY' ? 'Rally' : 'Side-Out'} · ${titleTab === 'SOLO' ? pointsToWin : 11} · ${titleTab === 'SOLO' ? (bestOf === 3 ? 'Best of 3' : '1 Game') : '0-0-2'}`}
                    </span>
                  </button>

                  {showMatchSettings && (
                    <div
                      className="absolute inset-0 z-30 flex items-end bg-black/60"
                      onClick={() => setShowMatchSettings(false)}
                    >
                      <div
                        className="flex w-full flex-col gap-2 rounded-t-2xl border-t border-white/10 bg-[#0E172A] p-4 pb-5"
                        onClick={(ev) => ev.stopPropagation()}
                      >
                        <div className="mb-1 flex items-center justify-between">
                          <span className="text-sm font-bold text-white">Match settings</span>
                          <button
                            type="button"
                            onClick={() => {
                              soundFx.playMenuClick();
                              setShowMatchSettings(false);
                            }}
                            className="rounded-lg bg-[#84CC16] px-3 py-1 text-xs font-bold text-[#0B1220] cursor-pointer"
                          >
                            Done
                          </button>
                        </div>
                        <div className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">Scoring</div>
                        <div className="grid w-full grid-cols-2 gap-1 rounded-xl border border-white/10 bg-[#0B1220]/85 p-1">
                    <button
                      type="button"
                      onClick={() => {
                        soundFx.playMenuClick();
                        setScoringMode('RALLY');
                      }}
                      className={`rounded-lg py-2 text-xs font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                        scoringMode === 'RALLY'
                          ? 'bg-[#1E293B] text-white'
                          : 'text-slate-400 hover:text-slate-200'
                      }`}
                    >
                      Rally to {titleTab === 'SOLO' ? pointsToWin : 11}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        soundFx.playMenuClick();
                        setScoringMode('SIDE_OUT');
                      }}
                      className={`rounded-lg py-2 text-xs font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                        scoringMode === 'SIDE_OUT'
                          ? 'bg-[#1E293B] text-white'
                          : 'text-slate-400 hover:text-slate-200'
                      }`}
                    >
                      Side-Out to {titleTab === 'SOLO' ? pointsToWin : 11}
                    </button>
                  </div>

                  <div className="mt-1 text-[10px] font-semibold uppercase tracking-wide text-slate-400">Opponent level</div>
                      <div
                    className="grid w-full grid-cols-4 gap-1 rounded-xl border border-white/10 bg-[#0B1220]/85 p-1"
                    title="Opponent level"
                  >
                    {(['EASY', 'NORMAL', 'HARD', 'PRO'] as const).map((level) => (
                      <button
                        key={level}
                        type="button"
                        onClick={() => {
                          soundFx.playMenuClick();
                          matchFormat.difficulty = level;
                          setDifficulty(level);
                        }}
                        className={`rounded-lg py-1.5 text-[11px] font-semibold transition-colors whitespace-nowrap cursor-pointer ${
                          difficulty === level
                            ? level === 'EASY'
                              ? 'bg-[#84CC16]/25 text-[#D9F99D]'
                              : level === 'PRO'
                                ? 'bg-[#F97316]/25 text-[#FED7AA]'
                                : 'bg-[#1E293B] text-white'
                            : 'text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        {BOT_PROFILES[level].label}
                      </button>
                    ))}
                  </div>

                  {titleTab === 'SOLO' && (
                        <div className="mt-1 text-[10px] font-semibold uppercase tracking-wide text-slate-400">Game length (win by 2)</div>
                      )}
                      {titleTab === 'SOLO' && (
                    <div className="grid w-full grid-cols-2 gap-1">
                      <div
                        className="grid grid-cols-3 gap-1 rounded-xl border border-white/10 bg-[#0B1220]/85 p-1"
                        title="Points to win a game (must win by 2)"
                      >
                        {([11, 15, 21] as const).map((pts) => (
                          <button
                            key={pts}
                            type="button"
                            onClick={() => {
                              soundFx.playMenuClick();
                              matchFormat.pointsToWin = pts;
                              setPointsToWin(pts);
                            }}
                            className={`rounded-lg py-1.5 text-[11px] font-semibold transition-colors whitespace-nowrap cursor-pointer ${
                            pointsToWin === pts
                              ? 'bg-[#1E293B] text-white'
                              : 'text-slate-400 hover:text-slate-200'
                          }`}
                          >
                            {pts}
                          </button>
                        ))}
                      </div>
                      <div
                        className="grid grid-cols-2 gap-1 rounded-xl border border-white/10 bg-[#0B1220]/85 p-1"
                        title="Single game or best of 3 games"
                      >
                        {([1, 3] as const).map((games) => (
                          <button
                            key={games}
                            type="button"
                            onClick={() => {
                              soundFx.playMenuClick();
                              matchFormat.bestOf = games;
                              setBestOf(games);
                            }}
                            className={`rounded-lg py-1.5 text-[11px] font-semibold transition-colors whitespace-nowrap cursor-pointer ${
                            bestOf === games
                              ? 'bg-[#1E293B] text-white'
                              : 'text-slate-400 hover:text-slate-200'
                          }`}
                          >
                            {games === 1 ? '1 Game' : 'Best of 3'}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                      </div>
                    </div>
                  )}

                  {titleTab === 'DOUBLES' && (
                    <div className="flex items-center justify-between rounded-lg border border-white/10 bg-[#0B1220]/80 px-3 py-1.5 text-[11px] text-slate-300">
                      <span>
                        Team: <strong className="text-white">{playerPalette.name} &amp; {partnerPalette.name}</strong>
                      </span>
                      <span className="font-mono text-[10px] text-[#84CC16]">Start 0-0-2</span>
                    </div>
                  )}

                  <button
                    type="button"
                    onClick={() => void handleEnterSoloCourt()}
                    className="flex min-h-[44px] w-full items-center justify-center gap-2 rounded-xl bg-[#84CC16] px-5 py-2.5 text-sm font-bold text-[#0B1220] transition-transform duration-150 hover:bg-[#95DF26] active:scale-[0.98] whitespace-nowrap shrink-0 cursor-pointer"
                  >
                    <Play className="h-4 w-4 fill-current" />
                    <span>
                      {titleTab === 'DOUBLES'
                        ? `Play 2v2 (${playerPalette.name} & ${partnerPalette.name})`
                        : `Play vs ${opponentPalette.name} · ${BOT_PROFILES[difficulty].label}`}
                    </span>
                  </button>
                </>
              ) : titleTab === 'NEARBY' ? (
                <div className="flex flex-col gap-2.5 rounded-xl border border-white/10 bg-[#0B1220]/90 p-3">
                  <div className="text-[11px] leading-snug text-slate-300">
                    Play 1v1 with someone next to you on the same Wi-Fi or hotspot. No account or
                    internet needed.
                  </div>
                  <button
                    type="button"
                    disabled={roomBusy}
                    onClick={() => void startHostRoom(true)}
                    className="flex items-center justify-center gap-1.5 rounded-xl bg-[#84CC16] py-2.5 text-sm font-bold text-[#0B1220] transition-colors hover:bg-[#A3E635] disabled:opacity-60 cursor-pointer"
                  >
                    <Wifi className="h-4 w-4" />
                    <span>Host Nearby Game</span>
                  </button>
                  <div className="flex items-center justify-between text-[11px] text-slate-400">
                    <span className="font-semibold text-slate-300">Players nearby</span>
                    <span className="animate-pulse">Searching…</span>
                  </div>
                  {nearbyHosts.length === 0 ? (
                    <div className="rounded-lg border border-dashed border-slate-700 px-3 py-3 text-center text-[11px] text-slate-500">
                      No games found yet. Ask your friend to tap "Host Nearby Game".
                    </div>
                  ) : (
                    <div className="flex flex-col gap-1.5">
                      {nearbyHosts.map((host) => (
                        <button
                          key={host.roomCode}
                          type="button"
                          disabled={roomBusy}
                          onClick={() => void startJoinRoom(host.roomCode, true)}
                          className="flex items-center gap-2 rounded-lg border border-slate-700 bg-[#121C30] px-2.5 py-2 text-left transition-colors hover:border-[#84CC16] disabled:opacity-60 cursor-pointer"
                        >
                          <CharacterPortrait characterId={host.characterId} size={28} locked={false} />
                          <div className="min-w-0 flex-1">
                            <div className="truncate text-xs font-semibold text-white">
                              {host.hostName}&apos;s court
                            </div>
                            <div className="font-mono text-[10px] text-slate-400">{host.roomCode}</div>
                          </div>
                          <span className="text-[11px] font-bold text-[#84CC16]">Join</span>
                        </button>
                      ))}
                    </div>
                  )}
                  {roomError && <div className="text-[11px] text-rose-400">{roomError}</div>}
                  <div className="text-[10px] leading-snug text-slate-500">
                    Web preview: finds games in other tabs of this browser. Android app: finds phones
                    nearby over Wi-Fi, hotspot or Bluetooth.
                  </div>
                </div>
              ) : !ONLINE_ENABLED ? (
                <div className="flex flex-col items-center gap-2 rounded-xl border border-white/10 bg-[#0B1220]/90 px-3 py-5 text-center">
                  <Users className="h-6 w-6 text-slate-400" />
                  <div className="text-sm font-bold text-white">This feature is available soon</div>
                  <div className="text-[11px] leading-snug text-slate-400">
                    Online 1v1 is coming in a future update. For now, play Solo, 2v2, or Nearby with
                    a friend next to you.
                  </div>
                </div>
              ) : (
                <div className="flex flex-col gap-2.5 rounded-xl border border-white/10 bg-[#0B1220]/90 p-3">
                  <div className="flex items-center justify-between gap-2 border-b border-slate-800 pb-2 text-[11px]">
                    {authUser ? (
                      <>
                        <span className="truncate font-mono text-slate-300">
                          {authUser.isAnonymous
                            ? `Anon · ${authUser.uid.slice(0, 6)}`
                            : authUser.displayName || authUser.email}
                        </span>
                        <button
                          type="button"
                          onClick={() => void signOutPlayer()}
                          className="flex items-center gap-1 rounded-md border border-slate-700 px-2 py-1 text-[10px] text-slate-300 hover:bg-slate-800 whitespace-nowrap shrink-0 cursor-pointer"
                        >
                          <LogOut className="h-3 w-3" />
                          <span>Sign out</span>
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          type="button"
                          disabled={authBusy}
                          onClick={() => void handleGoogleSignIn()}
                          className="flex flex-1 items-center justify-center gap-1 rounded-md border border-slate-700 bg-[#121C30] py-1.5 text-[11px] font-medium text-white hover:bg-slate-800 disabled:opacity-50 whitespace-nowrap shrink-0 cursor-pointer"
                        >
                          <LogIn className="h-3 w-3" />
                          <span>Google Sign-In</span>
                        </button>
                        <button
                          type="button"
                          disabled={authBusy}
                          onClick={() => void handleAnonSignIn()}
                          className="flex flex-1 items-center justify-center rounded-md border border-slate-700 bg-[#121C30] py-1.5 text-[11px] font-medium text-slate-200 hover:bg-slate-800 disabled:opacity-50 whitespace-nowrap shrink-0 cursor-pointer"
                        >
                          <span>Anon Sign-In</span>
                        </button>
                      </>
                    )}
                  </div>

                  <button
                    type="button"
                    disabled={roomBusy}
                    onClick={() => void handleCreateRoom()}
                    className="flex min-h-[42px] w-full items-center justify-center gap-2 rounded-lg bg-[#84CC16] px-4 py-2 text-xs font-bold text-[#0B1220] transition-colors hover:bg-[#95DF26] disabled:opacity-50 whitespace-nowrap shrink-0 cursor-pointer"
                  >
                    <Users className="h-3.5 w-3.5" />
                    <span>Create 1v1 Room (Host)</span>
                  </button>

                  <div className="flex items-center gap-1.5">
                    <input
                      type="text"
                      value={roomInput}
                      onChange={(e) => setRoomInput(e.target.value.toUpperCase())}
                      placeholder="PKL-482"
                      maxLength={7}
                      aria-label="Room code"
                      className="min-w-0 flex-1 rounded-lg border border-slate-700 bg-[#121C30] px-3 py-2 font-mono text-xs uppercase text-white placeholder:text-slate-500 focus:border-[#84CC16] focus:outline-none"
                    />
                    <button
                      type="button"
                      disabled={roomBusy}
                      onClick={() => void handleJoinRoom()}
                      className="rounded-lg border border-slate-600 bg-[#1E293B] px-3.5 py-2 text-xs font-semibold text-white hover:bg-slate-700 disabled:opacity-50 whitespace-nowrap shrink-0 cursor-pointer"
                    >
                      Join Room
                    </button>
                  </div>

                  {roomError && (
                    <p className="text-[11px] text-rose-300 leading-snug">{roomError}</p>
                  )}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Phase 5 Character Select Screen */}
        {gameState === 'CHARACTER_SELECT' && (
          <div className="flex h-full w-full flex-col justify-between overflow-y-auto bg-[#0E172A] p-4">
            <div>
              <div className="flex items-center justify-between gap-2 pb-3 border-b border-slate-800">
                <button
                  type="button"
                  onClick={() => {
                    soundFx.playMenuClick();
                    setGameState('TITLE_MENU');
                  }}
                  className="flex items-center gap-1.5 rounded-lg border border-slate-700 bg-[#121C30] px-2.5 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-800 whitespace-nowrap shrink-0 cursor-pointer"
                >
                  <ArrowLeft className="h-3.5 w-3.5" />
                  <span>Back</span>
                </button>
                <div className="text-right">
                  <h2 className="font-display text-sm font-bold text-white">Select Fighter</h2>
                  <div className="text-[10px] text-slate-400">Cosmetic only · Equal stats</div>
                </div>
              </div>

              <div className="mt-3 grid grid-cols-3 gap-2">
                {ROSTER_ORDER.map((id) => {
                  const pal = CHARACTER_PALETTES[id];
                  const locked = Boolean(pal.isUnlockable && !dillUnlocked);
                  const selected = playerChar === id;
                  return (
                    <button
                      key={id}
                      type="button"
                      onClick={() => handleSelectCharacter(id)}
                      className={`flex flex-col items-center rounded-xl border p-2 text-center transition-colors duration-150 cursor-pointer ${
                        selected
                          ? 'border-[#84CC16] bg-[#162642]'
                          : 'border-slate-800 bg-[#121C30] hover:border-slate-700'
                      }`}
                    >
                      <CharacterPortrait characterId={id} size={44} locked={locked} />
                      <div className="mt-2 flex items-center gap-1 text-xs font-bold text-white">
                        <span>{pal.name}</span>
                        {locked && <Lock className="h-3 w-3 text-slate-400" />}
                      </div>
                      <div className="text-[11px] font-medium text-[#84CC16]">{pal.tagline}</div>
                      <div className="mt-0.5 text-[10px] text-slate-400 leading-tight">
                        {pal.description}
                      </div>
                    </button>
                  );
                })}
              </div>

              <button
                type="button"
                onClick={handleToggleDillUnlock}
                className="mt-3 flex min-h-[38px] w-full items-center justify-center gap-1.5 rounded-lg border border-slate-700 bg-[#121C30] px-3 py-2 text-xs font-medium text-slate-200 transition-colors hover:bg-slate-800 whitespace-nowrap shrink-0 cursor-pointer"
              >
                {dillUnlocked ? <Unlock className="h-3.5 w-3.5 text-[#84CC16]" /> : <Lock className="h-3.5 w-3.5 text-amber-400" />}
                <span>{dillUnlocked ? 'Dill Mascot Unlocked (Tap to Lock)' : 'Unlock Dill Mascot'}</span>
              </button>
            </div>

            <div className="space-y-2 border-t border-slate-800 pt-2.5">
              <div>
                <div className="mb-1 text-[10px] font-medium text-slate-400">
                  2v2 Doubles Partner
                </div>
                <div className="grid grid-cols-5 gap-1 rounded-lg border border-slate-800 bg-[#121C30] p-1">
                  {ROSTER_ORDER.map((id) => {
                    const active = partnerChar === id;
                    return (
                      <button
                        key={id}
                        type="button"
                        onClick={() => {
                          soundFx.playMenuClick();
                          setPartnerChar(id);
                        }}
                        className={`rounded-md py-1 text-[11px] font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                          active
                            ? 'bg-[#38BDF8] text-[#0B1220]'
                            : 'text-slate-300 hover:bg-slate-800'
                        }`}
                      >
                        {CHARACTER_PALETTES[id].name}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div>
                <div className="mb-1 text-[10px] font-medium text-slate-400">
                  Primary Opponent
                </div>
                <div className="grid grid-cols-5 gap-1 rounded-lg border border-slate-800 bg-[#121C30] p-1">
                  {ROSTER_ORDER.map((id) => {
                    const active = opponentChar === id;
                    return (
                      <button
                        key={id}
                        type="button"
                        onClick={() => {
                          soundFx.playMenuClick();
                          setOpponentChar(id);
                        }}
                        className={`rounded-md py-1 text-[11px] font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                          active
                            ? 'bg-[#F97316] text-[#0B1220]'
                            : 'text-slate-300 hover:bg-slate-800'
                        }`}
                      >
                        {CHARACTER_PALETTES[id].name}
                      </button>
                    );
                  })}
                </div>
              </div>

              <button
                type="button"
                onClick={() => {
                  soundFx.playMenuClick();
                  setGameState('TITLE_MENU');
                }}
                className="flex min-h-[40px] w-full items-center justify-center rounded-xl bg-[#84CC16] px-4 py-2 text-xs font-bold text-[#0B1220] hover:bg-[#95DF26] whitespace-nowrap shrink-0 cursor-pointer"
              >
                Confirm {playerPalette.name}
              </button>
            </div>
          </div>
        )}

        {/* Phase 6 Profile, Match History & Leaderboard */}
        {gameState === 'LEARN' && <LearnView onBack={() => setGameState('TITLE_MENU')} />}

        {gameState === 'STATS_HUB' && (
          <StatsHubView
            user={authUser}
            playerChar={playerChar}
            onBack={() => setGameState('TITLE_MENU')}
          />
        )}

        {/* Active Match Court */}
        {(gameState === 'PLAYING' || gameState === 'PAUSED' || gameState === 'GAME_OVER') && (
          <div className="relative flex h-full w-full flex-col bg-[#15223B]">
            {/* Top Perimeter Scoreboard Bar ABOVE the canvas (never covers Lola) */}
            <div className="shrink-0 flex items-center justify-between gap-2 border-b border-white/10 bg-[#0B1220] px-3 py-2">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5 whitespace-nowrap font-mono text-xs tabular-nums text-white">
                  <span className={hud.servingSide === 'LOWER' ? 'text-[#84CC16] font-bold' : ''}>
                    {scoreLocalLabel ?? localLabel} {hud.scoreLower}
                  </span>
                  <span className="text-slate-500">–</span>
                  <span className={hud.servingSide === 'UPPER' ? 'text-[#F97316] font-bold' : ''}>
                    {hud.scoreUpper} {scoreRemoteLabel ?? remoteLabel}
                  </span>
                  {hud.matchStatus && (
                    <span
                      className="ml-0.5 shrink-0 rounded bg-white/10 px-1 py-0.5 font-sans text-[9px] font-semibold text-slate-300"
                      title="Current game · games won (you-opponent)"
                    >
                      {hud.matchStatus}
                    </span>
                  )}
                </div>
                <div className="mt-0.5 text-[11px] font-medium text-slate-200 leading-tight truncate">
                  {rulePhaseLabel}
                </div>
              </div>

              <div className="flex items-center gap-1 shrink-0">
                {isOnline && (
                  <button
                    type="button"
                    onClick={() => void handleCopyRoomCode()}
                    title="Copy Room Code"
                    className="flex h-8 items-center gap-1 rounded-lg border border-white/15 bg-[#121C30] px-2 font-mono text-[11px] font-semibold text-[#84CC16] hover:bg-[#1E293B] whitespace-nowrap shrink-0 cursor-pointer"
                  >
                    <span>{onlineSession.room.roomCode}</span>
                    {copiedRoomCode ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
                  </button>
                )}
                {isNearby && (
                  <span className="flex h-8 items-center gap-1 rounded-lg border border-white/15 bg-[#121C30] px-2 text-[11px] font-semibold text-[#84CC16] whitespace-nowrap shrink-0">
                    <Wifi className="h-3 w-3" />
                    <span>Nearby</span>
                  </span>
                )}
                {!hasNetworkSession && (
                  <button
                    type="button"
                    onClick={() => setShowDimensions((prev) => !prev)}
                    aria-label="Toggle court dimensions"
                    title="Toggle dimensions (D)"
                    className={`flex h-8 w-8 items-center justify-center rounded-lg border transition-colors duration-150 cursor-pointer ${
                      showDimensions
                        ? 'border-[#84CC16]/70 bg-[#84CC16] text-[#0B1220]'
                        : 'border-white/15 bg-[#121C30] text-slate-200 hover:bg-[#1E293B]'
                    }`}
                  >
                    <Ruler className="h-3.5 w-3.5" />
                  </button>
                )}
                <button
                  type="button"
                  onClick={toggleMute}
                  aria-label={muted ? 'Unmute audio' : 'Mute audio'}
                  title="Mute / Unmute (M)"
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-white/15 bg-[#121C30] text-slate-200 transition-colors duration-150 hover:bg-[#1E293B] cursor-pointer"
                >
                  {muted ? <VolumeX className="h-3.5 w-3.5" /> : <Volume2 className="h-3.5 w-3.5" />}
                </button>
                <button
                  type="button"
                  onClick={() => setGameState('PAUSED')}
                  aria-label="Open match menu"
                  title="Menu (Esc)"
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-white/15 bg-[#121C30] text-slate-200 transition-colors duration-150 hover:bg-[#1E293B] cursor-pointer"
                >
                  <Pause className="h-3.5 w-3.5" />
                </button>
              </div>
            </div>

            {/* Shrinking Court Container */}
            <div className="relative min-h-0 flex-1 w-full">
              <CourtCanvas
                scoringMode={scoringMode}
                matchMode={isDoubles ? 'DOUBLES' : 'SINGLES'}
                playerCharacter={playerChar}
                partnerCharacter={partnerChar}
                opponentCharacter={opponentChar}
                showDimensions={showDimensions}
                serveRequestNonce={serveNonce}
                receiveReadyNonce={receiveNonce}
                resetRequestNonce={resetNonce}
                paused={gameState === 'PAUSED' || gameState === 'GAME_OVER'}
                onlineSession={onlineSession}
                simulatedGuestActive={simGuestActive}
                playerName={playerDisplayName || undefined}
                onHudChange={handleHudChange}
              />

              {/* One-time first-serve controls hint overlay */}
              {!hasSeenControlsHint &&
                !hasNetworkSession &&
                gameState === 'PLAYING' &&
                hud.rallyPhase === 'WAITING_SERVE' &&
                hud.scoreLower === 0 &&
                hud.scoreUpper === 0 && (
                  <div className="pointer-events-none absolute inset-x-0 top-1/2 -translate-y-1/2 flex justify-center px-4">
                    <div className="rounded-xl border border-[#84CC16]/55 bg-[#0B1220]/92 px-3.5 py-2 text-center shadow-lg">
                      <span className="text-[11px] font-semibold text-white">
                        Tap to move · Stand in the ring to hit
                      </span>
                    </div>
                  </div>
                )}

              {/* Phase 5 Point / Fault Announcement Banner during POINT_PAUSE */}
              {hud.rallyPhase === 'POINT_PAUSE' && hud.lastCallout && (
                <div className="pointer-events-none absolute inset-x-0 top-3 flex justify-center px-4">
                  <div
                    className={`rounded-xl border px-4 py-2 text-center shadow-lg transition-opacity duration-150 ${
                      isFaultCallout
                        ? 'border-rose-400/50 bg-[#7F1D1D]/92 text-white'
                        : 'border-[#84CC16]/50 bg-[#0B1220]/92 text-[#84CC16]'
                    }`}
                  >
                    <div className="font-display text-sm font-bold">{hud.lastCallout}</div>
                    <div className="font-mono text-[11px] tabular-nums text-slate-200">
                      {localLabel} {hud.scoreLower} – {hud.scoreUpper} {remoteLabel}
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Bottom Controls Bar BELOW the canvas with safe-area padding */}
            <div className="shrink-0 flex flex-wrap items-center justify-center gap-2 border-t border-white/10 bg-[#0B1220] px-3 pt-2 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
              {isOnline && onlineSession.room.isHost && !opponentConnected && (
                <button
                  type="button"
                  onClick={() => setSimGuestActive((prev) => !prev)}
                  className={`flex min-h-[36px] items-center gap-1.5 rounded-lg border px-3 py-1.5 text-[11px] font-semibold whitespace-nowrap shrink-0 cursor-pointer ${
                    simGuestActive
                      ? 'border-[#84CC16] bg-[#84CC16] text-[#0B1220]'
                      : 'border-slate-600 bg-[#121C30] text-slate-200 hover:bg-[#1E293B]'
                  }`}
                >
                  <Bot className="h-3.5 w-3.5" />
                  <span>{simGuestActive ? 'Sim Guest: ON' : 'Sim Guest'}</span>
                </button>
              )}

              {hud.rallyPhase === 'WAITING_SERVE' &&
              hud.servingSide === 'LOWER' &&
              (!isDoubles || hud.serverIndexInTeam === 0) ? (
                <button
                  type="button"
                  onClick={() => {
                    markControlsHintSeen();
                    setServeNonce((n) => n + 1);
                  }}
                  className="flex min-h-[40px] items-center gap-2 rounded-xl bg-[#84CC16] px-5 py-2 text-xs font-bold text-[#0B1220] shadow-lg transition-transform duration-150 hover:bg-[#95DF26] active:scale-[0.98] whitespace-nowrap shrink-0 cursor-pointer"
                >
                  <Play className="h-3.5 w-3.5 fill-current" />
                  <span>Serve Diagonal</span>
                </button>
              ) : hud.rallyPhase === 'WAITING_SERVE' && !hasNetworkSession ? (
                <button
                  type="button"
                  onClick={() => setReceiveNonce((n) => n + 1)}
                  className="flex min-h-[40px] w-full items-center justify-center gap-2 rounded-xl border border-[#F97316]/60 bg-[#121C30] px-4 py-2 text-xs font-semibold text-white transition-colors duration-150 hover:bg-[#1E293B] whitespace-nowrap shrink-0 cursor-pointer"
                >
                  <span className="text-[#F97316]">
                    {isDoubles && hud.servingSide === 'LOWER'
                      ? `${partnerPalette.name} serving…`
                      : `${remoteLabel} serving…`}
                    {hud.botServeCountdownSec > 0 ? ` ${hud.botServeCountdownSec}s` : ''}
                  </span>
                  <span className="text-slate-400">·</span>
                  <span>{isDoubles && hud.servingSide === 'LOWER' ? 'Tap to start' : 'Tap to receive'}</span>
                </button>
              ) : (
                <div className="flex min-h-[40px] w-full items-center justify-between gap-2 whitespace-nowrap rounded-md border border-white/10 bg-[#121C30] px-3 py-1.5 text-[11px] font-mono tabular-nums text-slate-200">
                  <span className="min-w-0 truncate">
                    {hud.rallyPhase === 'WAITING_SERVE' && isDoubles && hud.servingSide === 'LOWER'
                      ? `${partnerPalette.name} serving...`
                      : hud.servingSide === 'LOWER'
                        ? `${localLabel} serving`
                        : `${remoteLabel} serving`}
                    {hud.rallyPhase === 'RALLY_LIVE' && (
                      <span className="text-[#84CC16]"> · {rulePhaseLabel}</span>
                    )}
                  </span>
                  {hud.rallyHits >= 3 && (
                    <span className="shrink-0 text-slate-300">Rally {hud.rallyHits}</span>
                  )}
                </div>
              )}
            </div>

            {/* Pause Modal */}
            {gameState === 'PAUSED' && (
              <div className="absolute inset-0 z-20 flex items-center justify-center bg-[#0B1220]/82 p-6">
                <div className="w-full max-w-[260px] rounded-2xl border border-slate-700 bg-[#121C30] p-5 shadow-2xl">
                  <h2 className="font-display text-lg font-bold text-white">
                    {isNearby ? 'Nearby 1v1' : isOnline ? `Room ${onlineSession.room.roomCode}` : 'Match Paused'}
                  </h2>
                  <p className="mt-1 text-xs text-slate-300 leading-relaxed">
                    {hasNetworkSession
                      ? onlineSession.room.isHost
                        ? 'Host runs official 60 Hz physics and publishes 20 Hz state.'
                        : 'Guest predicts movement locally and interpolates Host state.'
                      : isDoubles
                        ? `2v2 Doubles (${playerPalette.name} & ${partnerPalette.name}) · ${scoringMode === 'RALLY' ? 'Rally to 11' : 'Side-Out to 11'}.`
                        : `${playerPalette.name} vs ${opponentPalette.name} · ${scoringMode === 'RALLY' ? 'Rally to 11' : 'Side-Out to 11'}.`}
                  </p>

                  <div className="mt-4 flex flex-col gap-2">
                    <button
                      type="button"
                      onClick={() => setGameState('PLAYING')}
                      className="flex min-h-[42px] w-full items-center justify-center gap-2 rounded-lg bg-[#84CC16] px-4 py-2 text-xs font-bold text-[#0B1220] transition-colors duration-150 hover:bg-[#95DF26] whitespace-nowrap shrink-0 cursor-pointer"
                    >
                      <Maximize2 className="h-3.5 w-3.5" />
                      <span>Resume Match</span>
                    </button>
                    {!hasNetworkSession && (
                      <>
                        <button
                          type="button"
                          onClick={() => {
                            savedMatchGuardRef.current = false;
                            setMatchSaveState('IDLE');
                            setResetNonce((n) => n + 1);
                            setGameState('PLAYING');
                          }}
                          className="flex min-h-[42px] w-full items-center justify-center gap-2 rounded-lg border border-slate-600 bg-slate-800/80 px-4 py-2 text-xs font-semibold text-slate-100 transition-colors duration-150 hover:bg-slate-700 whitespace-nowrap shrink-0 cursor-pointer"
                        >
                          <RotateCcw className="h-3.5 w-3.5" />
                          <span>Restart Match</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            soundFx.playMenuClick();
                            setGameState('CHARACTER_SELECT');
                          }}
                          className="flex min-h-[40px] w-full items-center justify-center gap-2 rounded-lg border border-slate-700 bg-transparent px-4 py-2 text-xs font-medium text-slate-200 transition-colors duration-150 hover:bg-slate-800 whitespace-nowrap shrink-0 cursor-pointer"
                        >
                          <span>Change Character</span>
                        </button>
                      </>
                    )}
                    <button
                      type="button"
                      onClick={() => {
                        if (hasNetworkSession) {
                          void handleLeaveOnlineRoom();
                        } else {
                          setGameState('TITLE_MENU');
                        }
                      }}
                      className="flex min-h-[40px] w-full items-center justify-center gap-2 rounded-lg border border-slate-700 bg-transparent px-4 py-2 text-xs font-medium text-slate-300 transition-colors duration-150 hover:bg-slate-800 whitespace-nowrap shrink-0 cursor-pointer"
                    >
                      <span>{hasNetworkSession ? 'Leave Room' : 'Return to Title'}</span>
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* Match Victory / Host Disconnect Modal */}
            {gameState === 'GAME_OVER' && (
              <div className="absolute inset-0 z-20 flex items-center justify-center bg-[#0B1220]/86 p-6">
                <div className="w-full max-w-[260px] rounded-2xl border border-slate-700 bg-[#121C30] p-5 text-center shadow-2xl">
                  <div className="mx-auto mb-3 flex justify-center">
                    {hud.hostDisconnected ? (
                      <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-amber-500/15 text-amber-400">
                        <Trophy className="h-5 w-5" />
                      </div>
                    ) : (
                      <CharacterPortrait
                        characterId={
                          hud.matchWinner === 'LOWER' ? playerChar : opponentChar
                        }
                        size={60}
                      />
                    )}
                  </div>
                  <h2 className="font-display text-xl font-bold text-white">
                    {hud.hostDisconnected
                      ? 'Host Left Room'
                      : hud.matchWinner === 'LOWER'
                        ? `${localLabel} Wins!`
                        : `${remoteLabel} Wins!`}
                  </h2>
                  {hud.gameScoresSummary ? (
                    <div className="mt-1 space-y-0.5">
                      <p className="font-mono text-sm tabular-nums text-slate-200">
                        Games: {hud.gamesLower} – {hud.gamesUpper}
                      </p>
                      <p className="font-mono text-[11px] tabular-nums text-slate-400">
                        {hud.gameScoresSummary}
                      </p>
                    </div>
                  ) : (
                    <p className="mt-1 font-mono text-sm tabular-nums text-slate-200">
                      Score: {hud.scoreLower} – {hud.scoreUpper}
                    </p>
                  )}
                  <p className="mt-1 text-xs text-slate-400">
                    {hud.hostDisconnected
                      ? 'The host closed the room gracefully.'
                      : `Longest rally: ${hud.bestRally} shots`}
                  </p>
                  {!hud.hostDisconnected && matchSaveState !== 'IDLE' && (
                    <p className="mt-1.5 font-mono text-[10px] text-[#84CC16]">
                      {matchSaveState === 'SAVING'
                        ? 'Saving match result...'
                        : matchSaveState === 'CLOUD'
                          ? 'Saved to Cloud History & Leaderboard'
                          : 'Saved to Local Match History'}
                    </p>
                  )}

                  <div className="mt-5 flex flex-col gap-2">
                    {!hasNetworkSession && (
                      <button
                        type="button"
                        onClick={() => {
                          savedMatchGuardRef.current = false;
                          setMatchSaveState('IDLE');
                          setResetNonce((n) => n + 1);
                          setGameState('PLAYING');
                        }}
                        className="flex min-h-[44px] w-full items-center justify-center gap-2 rounded-lg bg-[#84CC16] px-4 py-2 text-xs font-bold text-[#0B1220] transition-colors duration-150 hover:bg-[#95DF26] whitespace-nowrap shrink-0 cursor-pointer"
                      >
                        <RotateCcw className="h-3.5 w-3.5" />
                        <span>Play Again</span>
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => {
                        if (hasNetworkSession) {
                          void handleLeaveOnlineRoom();
                        } else {
                          setGameState('TITLE_MENU');
                        }
                      }}
                      className="flex min-h-[42px] w-full items-center justify-center gap-2 rounded-lg border border-slate-700 bg-transparent px-4 py-2 text-xs font-medium text-slate-200 transition-colors duration-150 hover:bg-slate-800 whitespace-nowrap shrink-0 cursor-pointer"
                    >
                      <span>{hasNetworkSession ? 'Back to Lobby' : 'Return to Title'}</span>
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
