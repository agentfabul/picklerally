import { initializeApp } from 'firebase/app';
import {
  GoogleAuthProvider,
  onAuthStateChanged,
  signInAnonymously,
  signInWithPopup,
  signOut,
  type User,
} from 'firebase/auth';
import { getAuth } from 'firebase/auth';
import {
  collection,
  doc,
  getDoc,
  getDocFromServer,
  getDocs,
  getFirestore,
  limit,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
} from 'firebase/firestore';
import { getDatabase, type Database } from 'firebase/database';
import firebaseConfig from '../../firebase-applet-config.json';
import { CHARACTER_PALETTES, type  CharacterId } from './ballPhysics';
import { nextPlayerRating, validateMatchPayload, type MatchRecordMode, type ScoringRuleMode, type WinnerSide } from './matchValidator';

export enum OperationType {
  CREATE = 'create',
  UPDATE = 'update',
  DELETE = 'delete',
  LIST = 'list',
  GET = 'get',
  WRITE = 'write',
}

export interface FirestoreErrorInfo {
  error: string;
  operationType: OperationType;
  path: string | null;
  authInfo: {
    userId: string | undefined;
    email: string | null | undefined;
    emailVerified: boolean | undefined;
    isAnonymous: boolean | undefined;
    tenantId: string | null | undefined;
    providerInfo: {
      providerId: string;
      displayName: string | null;
      email: string | null;
      photoUrl: string | null;
    }[];
  };
}

export interface UserProfileStats {
  uid: string;
  displayName: string;
  characterId: CharacterId;
  wins: number;
  losses: number;
  matchesPlayed: number;
  pointsScored: number;
  bestRally: number;
  rating: number;
}

export interface MatchHistoryItem {
  matchId: string;
  roomCode?: string;
  mode: MatchRecordMode;
  scoringMode: ScoringRuleMode;
  playerCharacter: CharacterId;
  partnerCharacter?: CharacterId;
  opponentCharacter: CharacterId;
  winnerSide: WinnerSide;
  scoreLower: number;
  scoreUpper: number;
  bestRally: number;
  validated: boolean;
  createdAtMs: number;
  source: 'CLOUD' | 'LOCAL';
}

export interface LeaderboardRow {
  uid: string;
  displayName: string;
  characterId: CharacterId;
  wins: number;
  losses: number;
  matchesPlayed: number;
  pointsScored: number;
  bestRally: number;
  rating: number;
}

const LOCAL_HISTORY_KEY = 'pickle-rally-local-history-v1';
const LOCAL_PROFILE_KEY = 'pickle-rally-local-profile-v1';

export const firebaseProjectConfig = firebaseConfig as typeof firebaseConfig & {
  databaseURL?: string;
};

export const app = initializeApp(firebaseProjectConfig);
export const auth = getAuth(app);
export const db = getFirestore(app, firebaseProjectConfig.firestoreDatabaseId);
export const rtdb: Database | null = firebaseProjectConfig.databaseURL
  ? getDatabase(app)
  : null;

export function handleFirestoreError(
  error: unknown,
  operationType: OperationType,
  path: string | null,
): never {
  const errInfo: FirestoreErrorInfo = {
    error: error instanceof Error ? error.message : String(error),
    authInfo: {
      userId: auth.currentUser?.uid,
      email: auth.currentUser?.email,
      emailVerified: auth.currentUser?.emailVerified,
      isAnonymous: auth.currentUser?.isAnonymous,
      tenantId: auth.currentUser?.tenantId,
      providerInfo:
        auth.currentUser?.providerData.map((provider) => ({
          providerId: provider.providerId,
          displayName: provider.displayName,
          email: provider.email,
          photoUrl: provider.photoURL,
        })) ?? [],
    },
    operationType,
    path,
  };
  console.error('Firestore Error:', JSON.stringify(errInfo));
  throw new Error(JSON.stringify(errInfo));
}

export async function testConnection(): Promise<boolean> {
  try {
    await getDocFromServer(doc(db, 'test', 'connection'));
    return true;
  } catch (error) {
    if (error instanceof Error && error.message.includes('the client is offline')) {
      console.error('Please check your Firebase configuration.');
    }
    return false;
  }
}

export function subscribeAuthUser(cb: (user: User | null) => void): () => void {
  return onAuthStateChanged(auth, cb);
}

export async function signInPlayerAnonymously(): Promise<User> {
  if (auth.currentUser) return auth.currentUser;
  const cred = await signInAnonymously(auth);
  return cred.user;
}

export async function signInPlayerWithGoogle(): Promise<User> {
  const provider = new GoogleAuthProvider();
  const cred = await signInWithPopup(auth, provider);
  return cred.user;
}

export async function signOutPlayer(): Promise<void> {
  await signOut(auth);
}

function normalizeCharId(raw: unknown, fallback: CharacterId = 'RIO'): CharacterId {
  return typeof raw === 'string' && raw in CHARACTER_PALETTES ? (raw as CharacterId) : fallback;
}

function sanitizeDisplayName(raw: string | null | undefined, uid: string): string {
  const cleaned = (raw ?? '').trim().slice(0, 32);
  return cleaned.length > 0 ? cleaned : `Player-${uid.slice(0, 5)}`;
}

function timestampToMs(val: unknown): number {
  if (val instanceof Timestamp) return val.toMillis();
  if (typeof val === 'number') return val;
  return Date.now();
}

export function loadLocalProfile(fallbackChar: CharacterId = 'RIO'): UserProfileStats {
  if (typeof window === 'undefined') {
    return {
      uid: 'local-player',
      displayName: 'Local Player',
      characterId: fallbackChar,
      wins: 0,
      losses: 0,
      matchesPlayed: 0,
      pointsScored: 0,
      bestRally: 0,
      rating: 1000,
    };
  }
  try {
    const raw = window.localStorage.getItem(LOCAL_PROFILE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<UserProfileStats>;
      return {
        uid: parsed.uid ?? 'local-player',
        displayName: parsed.displayName?.slice(0, 32) || 'Local Player',
        characterId: normalizeCharId(parsed.characterId, fallbackChar),
        wins: parsed.wins ?? 0,
        losses: parsed.losses ?? 0,
        matchesPlayed: parsed.matchesPlayed ?? 0,
        pointsScored: parsed.pointsScored ?? 0,
        bestRally: parsed.bestRally ?? 0,
        rating: parsed.rating ?? 1000,
      };
    }
  } catch {
    // Ignore storage corruption
  }
  return {
    uid: 'local-player',
    displayName: 'Local Player',
    characterId: fallbackChar,
    wins: 0,
    losses: 0,
    matchesPlayed: 0,
    pointsScored: 0,
    bestRally: 0,
    rating: 1000,
  };
}

export function saveLocalProfile(profile: UserProfileStats): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(LOCAL_PROFILE_KEY, JSON.stringify(profile));
  } catch {
    // Ignore quota errors
  }
}

export function loadLocalMatchHistory(): MatchHistoryItem[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(LOCAL_HISTORY_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as MatchHistoryItem[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function appendLocalMatchHistory(item: MatchHistoryItem): void {
  if (typeof window === 'undefined') return;
  try {
    const current = loadLocalMatchHistory();
    const next = [item, ...current.filter((m) => m.matchId !== item.matchId)].slice(0, 25);
    window.localStorage.setItem(LOCAL_HISTORY_KEY, JSON.stringify(next));
  } catch {
    // Ignore quota errors
  }
}

export async function ensureUserProfile(
  user: User,
  characterId: CharacterId = 'RIO',
): Promise<CharacterId> {
  const path = `users/${user.uid}`;
  const ref = doc(db, 'users', user.uid);
  try {
    const snap = await getDoc(ref);
    if (!snap.exists()) {
      const displayName = sanitizeDisplayName(user.displayName, user.uid);
      await setDoc(ref, {
        uid: user.uid,
        displayName,
        characterId,
        wins: 0,
        losses: 0,
        matchesPlayed: 0,
        pointsScored: 0,
        bestRally: 0,
        rating: 1000,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      return characterId;
    }
    return normalizeCharId(snap.data().characterId, characterId);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export async function fetchUserProfile(
  user: User | null,
  fallbackChar: CharacterId = 'RIO',
): Promise<UserProfileStats> {
  if (!user) return loadLocalProfile(fallbackChar);
  const path = `users/${user.uid}`;
  const ref = doc(db, 'users', user.uid);
  try {
    const snap = await getDoc(ref);
    if (!snap.exists()) {
      await ensureUserProfile(user, fallbackChar);
      const retrySnap = await getDoc(ref);
      const data = retrySnap.data() ?? {};
      return {
        uid: user.uid,
        displayName: sanitizeDisplayName(data.displayName as string | undefined, user.uid),
        characterId: normalizeCharId(data.characterId, fallbackChar),
        wins: (data.wins as number | undefined) ?? 0,
        losses: (data.losses as number | undefined) ?? 0,
        matchesPlayed: (data.matchesPlayed as number | undefined) ?? 0,
        pointsScored: (data.pointsScored as number | undefined) ?? 0,
        bestRally: (data.bestRally as number | undefined) ?? 0,
        rating: (data.rating as number | undefined) ?? 1000,
      };
    }
    const data = snap.data();
    return {
      uid: user.uid,
      displayName: sanitizeDisplayName(data.displayName as string | undefined, user.uid),
      characterId: normalizeCharId(data.characterId, fallbackChar),
      wins: (data.wins as number | undefined) ?? 0,
      losses: (data.losses as number | undefined) ?? 0,
      matchesPlayed: (data.matchesPlayed as number | undefined) ?? 0,
      pointsScored: (data.pointsScored as number | undefined) ?? 0,
      bestRally: (data.bestRally as number | undefined) ?? 0,
      rating: (data.rating as number | undefined) ?? 1000,
    };
  } catch (error) {
    handleFirestoreError(error, OperationType.GET, path);
  }
}

export async function saveUserCharacter(
  user: User,
  characterId: CharacterId,
): Promise<void> {
  const path = `users/${user.uid}`;
  const ref = doc(db, 'users', user.uid);
  try {
    const snap = await getDoc(ref);
    if (!snap.exists()) {
      await ensureUserProfile(user, characterId);
      return;
    }
    await updateDoc(ref, {
      characterId,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}

export async function updateUserDisplayName(
  user: User | null,
  rawName: string,
  fallbackChar: CharacterId = 'RIO',
): Promise<string> {
  const cleanName = rawName.trim().slice(0, 32);
  if (!cleanName) throw new Error('Display name cannot be empty');

  const localProf = loadLocalProfile(fallbackChar);
  saveLocalProfile({ ...localProf, displayName: cleanName });

  if (!user) return cleanName;

  const path = `users/${user.uid}`;
  const userRef = doc(db, 'users', user.uid);
  try {
    const snap = await getDoc(userRef);
    if (!snap.exists()) {
      await ensureUserProfile(user, fallbackChar);
    }
    await updateDoc(userRef, {
      displayName: cleanName,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }

  const lbRef = doc(db, 'leaderboard', user.uid);
  try {
    const lbSnap = await getDoc(lbRef);
    if (lbSnap.exists()) {
      await updateDoc(lbRef, {
        displayName: cleanName,
        updatedAt: serverTimestamp(),
      });
    }
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, `leaderboard/${user.uid}`);
  }

  return cleanName;
}

export interface RecordMatchInput {
  roomCode?: string;
  mode: MatchRecordMode;
  scoringMode: ScoringRuleMode;
  playerCharacter: CharacterId;
  partnerCharacter?: CharacterId;
  opponentCharacter: CharacterId;
  winnerSide: WinnerSide;
  scoreLower: number;
  scoreUpper: number;
  bestRally: number;
  extraParticipantUids?: string[];
}

export async function recordFinishedMatch(
  user: User | null,
  input: RecordMatchInput,
): Promise<{ savedToCloud: boolean; matchId: string }> {
  const uid = user?.uid ?? 'local-player';
  const participants = Array.from(
    new Set([uid, ...(input.extraParticipantUids ?? [])].filter((id) => id.trim().length > 0)),
  ).slice(0, 4);

  const outcome = validateMatchPayload({
    mode: input.mode,
    scoringMode: input.scoringMode,
    createdByUid: uid,
    participants,
    scoreLower: input.scoreLower,
    scoreUpper: input.scoreUpper,
    winnerSide: input.winnerSide,
    bestRally: input.bestRally,
    playerCharacter: input.playerCharacter,
    opponentCharacter: input.opponentCharacter,
  });

  const matchId = `m-${Date.now()}-${Math.floor(100 + Math.random() * 900)}`;
  const didWin = input.winnerSide === 'LOWER';

  // Always update local profile & history cache
  const localProf = loadLocalProfile(input.playerCharacter);
  const delta = didWin ? outcome.winnerRatingDelta : outcome.loserRatingDelta;
  const nextLocal: UserProfileStats = {
    ...localProf,
    characterId: input.playerCharacter,
    wins: localProf.wins + (didWin ? 1 : 0),
    losses: localProf.losses + (didWin ? 0 : 1),
    matchesPlayed: localProf.matchesPlayed + 1,
    pointsScored: localProf.pointsScored + input.scoreLower,
    bestRally: Math.max(localProf.bestRally, input.bestRally),
    rating: nextPlayerRating(localProf.rating, outcome.isValid ? delta : 0),
  };
  saveLocalProfile(nextLocal);
  appendLocalMatchHistory({
    matchId,
    roomCode: input.roomCode,
    mode: input.mode,
    scoringMode: input.scoringMode,
    playerCharacter: input.playerCharacter,
    partnerCharacter: input.partnerCharacter,
    opponentCharacter: input.opponentCharacter,
    winnerSide: input.winnerSide,
    scoreLower: input.scoreLower,
    scoreUpper: input.scoreUpper,
    bestRally: input.bestRally,
    validated: false,
    createdAtMs: Date.now(),
    source: 'LOCAL',
  });

  if (!user || !outcome.isValid) {
    return { savedToCloud: false, matchId };
  }

  // 1. Write /matches/{matchId} (validated: false, validated server-side when Cloud Functions run)
  const matchPath = `matches/${matchId}`;
  try {
    await setDoc(doc(db, 'matches', matchId), {
      matchId,
      ...(input.roomCode ? { roomCode: input.roomCode } : {}),
      mode: input.mode,
      scoringMode: input.scoringMode,
      createdByUid: user.uid,
      participants,
      playerCharacter: input.playerCharacter,
      ...(input.partnerCharacter ? { partnerCharacter: input.partnerCharacter } : {}),
      opponentCharacter: input.opponentCharacter,
      winnerSide: input.winnerSide,
      scoreLower: input.scoreLower,
      scoreUpper: input.scoreUpper,
      bestRally: Math.min(999, Math.max(0, Math.round(input.bestRally))),
      validated: false,
      createdAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.CREATE, matchPath);
  }

  // 2. Persist bounded career summary & public leaderboard row
  const userProf = await fetchUserProfile(user, input.playerCharacter);
  const nextWins = userProf.wins + (didWin ? 1 : 0);
  const nextLosses = userProf.losses + (didWin ? 0 : 1);
  const nextMatches = nextWins + nextLosses;
  const nextPoints = userProf.pointsScored + input.scoreLower;
  const nextBest = Math.max(userProf.bestRally, Math.min(999, Math.round(input.bestRally)));
  const nextRating = nextPlayerRating(userProf.rating, delta);

  try {
    await updateDoc(doc(db, 'users', user.uid), {
      characterId: input.playerCharacter,
      wins: nextWins,
      losses: nextLosses,
      matchesPlayed: nextMatches,
      pointsScored: nextPoints,
      bestRally: nextBest,
      rating: nextRating,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, `users/${user.uid}`);
  }

  const lbPath = `leaderboard/${user.uid}`;
  const lbRef = doc(db, 'leaderboard', user.uid);
  try {
    const lbSnap = await getDoc(lbRef);
    if (!lbSnap.exists()) {
      await setDoc(lbRef, {
        uid: user.uid,
        displayName: userProf.displayName,
        characterId: input.playerCharacter,
        wins: didWin ? 1 : 0,
        losses: didWin ? 0 : 1,
        matchesPlayed: 1,
        pointsScored: input.scoreLower,
        bestRally: Math.min(999, Math.round(input.bestRally)),
        rating: nextPlayerRating(1000, delta),
        isPublic: true,
        updatedAt: serverTimestamp(),
      });
    } else {
      const prev = lbSnap.data();
      const prevW = (prev.wins as number | undefined) ?? 0;
      const prevL = (prev.losses as number | undefined) ?? 0;
      const prevPts = (prev.pointsScored as number | undefined) ?? 0;
      const prevBest = (prev.bestRally as number | undefined) ?? 0;
      const prevRating = (prev.rating as number | undefined) ?? 1000;
      const w = prevW + (didWin ? 1 : 0);
      const l = prevL + (didWin ? 0 : 1);
      await updateDoc(lbRef, {
        displayName: userProf.displayName,
        characterId: input.playerCharacter,
        wins: w,
        losses: l,
        matchesPlayed: w + l,
        pointsScored: prevPts + input.scoreLower,
        bestRally: Math.max(prevBest, Math.min(999, Math.round(input.bestRally))),
        rating: nextPlayerRating(prevRating, delta),
        updatedAt: serverTimestamp(),
      });
    }
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, lbPath);
  }

  return { savedToCloud: true, matchId };
}

export async function fetchMatchHistory(user: User | null): Promise<MatchHistoryItem[]> {
  if (!user) return loadLocalMatchHistory();
  const path = 'matches';
  try {
    const q = query(
      collection(db, 'matches'),
      where('participants', 'array-contains', user.uid),
      limit(25),
    );
    const snap = await getDocs(q);
    const rows: MatchHistoryItem[] = snap.docs.map((d) => {
      const data = d.data();
      return {
        matchId: String(data.matchId ?? d.id),
        roomCode: typeof data.roomCode === 'string' ? data.roomCode : undefined,
        mode: (data.mode as MatchRecordMode) ?? 'SOLO_1V1',
        scoringMode: (data.scoringMode as ScoringRuleMode) ?? 'RALLY',
        playerCharacter: normalizeCharId(data.playerCharacter, 'RIO'),
        partnerCharacter: data.partnerCharacter
          ? normalizeCharId(data.partnerCharacter, 'DILL')
          : undefined,
        opponentCharacter: normalizeCharId(data.opponentCharacter, 'LOLA'),
        winnerSide: (data.winnerSide as WinnerSide) ?? 'LOWER',
        scoreLower: Number(data.scoreLower ?? 0),
        scoreUpper: Number(data.scoreUpper ?? 0),
        bestRally: Number(data.bestRally ?? 0),
        validated: Boolean(data.validated),
        createdAtMs: timestampToMs(data.createdAt),
        source: 'CLOUD',
      };
    });
    rows.sort((a, b) => b.createdAtMs - a.createdAtMs);
    return rows;
  } catch (error) {
    handleFirestoreError(error, OperationType.LIST, path);
  }
}

export async function fetchLeaderboard(user: User | null, fallbackChar: CharacterId = 'RIO'): Promise<LeaderboardRow[]> {
  if (!user) {
    const localProf = loadLocalProfile(fallbackChar);
    return [
      {
        uid: localProf.uid,
        displayName: localProf.displayName,
        characterId: localProf.characterId,
        wins: localProf.wins,
        losses: localProf.losses,
        matchesPlayed: localProf.matchesPlayed,
        pointsScored: localProf.pointsScored,
        bestRally: localProf.bestRally,
        rating: localProf.rating,
      },
    ];
  }
  const path = 'leaderboard';
  try {
    const q = query(collection(db, 'leaderboard'), where('isPublic', '==', true), limit(50));
    const snap = await getDocs(q);
    const rows: LeaderboardRow[] = snap.docs.map((d) => {
      const data = d.data();
      return {
        uid: String(data.uid ?? d.id),
        displayName: String(data.displayName ?? 'Player'),
        characterId: normalizeCharId(data.characterId, 'RIO'),
        wins: Number(data.wins ?? 0),
        losses: Number(data.losses ?? 0),
        matchesPlayed: Number(data.matchesPlayed ?? 0),
        pointsScored: Number(data.pointsScored ?? 0),
        bestRally: Number(data.bestRally ?? 0),
        rating: Number(data.rating ?? 1000),
      };
    });
    rows.sort((a, b) => b.rating - a.rating || b.wins - a.wins);
    return rows;
  } catch (error) {
    handleFirestoreError(error, OperationType.LIST, path);
  }
}
