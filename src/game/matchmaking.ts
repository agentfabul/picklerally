import { get, onDisconnect, onValue, ref, remove, runTransaction, set, update } from 'firebase/database';
import type { User } from 'firebase/auth';
import type { CharacterId } from './ballPhysics';
import { auth, fetchLeaderboard, loadLocalProfile, rtdb, signInPlayerAnonymously } from './firebaseClient';
import { OnlineRoomSession, type RoomSessionCallbacks } from './onlineRoomService';

/**
 * Random-opponent matchmaking over Firebase Realtime Database.
 *
 * queue/{roomCode} = { host, name, characterId, rating, createdAt, claimedBy? }
 *  - A searching player first tries to claim the waiting entry with the closest rating
 *    (atomic transaction on claimedBy), then joins that room as guest and removes the entry.
 *  - If nobody is waiting, the player creates a room, publishes it in the queue and waits as host.
 * Android uses the same paths, so web and Android players can be matched together.
 */

export interface RankStatus {
  rating: number;
  /** 1-based position on the public leaderboard, or null when not ranked yet. */
  rank: number | null;
  total: number;
}

export interface OpponentProfile {
  name: string | null;
  characterId: CharacterId | null;
  rating: number | null;
}

interface QueueEntry {
  host: string;
  name?: string;
  characterId?: CharacterId;
  rating?: number;
  createdAt: number;
  claimedBy?: string;
}

const QUEUE_PATH = 'queue';
/** Waiting entries older than this are treated as abandoned. */
const STALE_MS = 90_000;

export async function fetchRankStatus(user: User | null, fallbackChar: CharacterId): Promise<RankStatus> {
  const rows = await fetchLeaderboard(user, fallbackChar);
  const sorted = [...rows].sort((a, b) => b.rating - a.rating);
  const myUid = user?.uid ?? loadLocalProfile(fallbackChar).uid;
  const idx = sorted.findIndex((r) => r.uid === myUid);
  const rating = idx >= 0 ? sorted[idx].rating : loadLocalProfile(fallbackChar).rating;
  return { rating, rank: user && idx >= 0 ? idx + 1 : null, total: sorted.length };
}

export async function findRandomMatch(
  callbacks: RoomSessionCallbacks,
  characterId: CharacterId,
  displayName: string,
  rating: number,
): Promise<{ session: OnlineRoomSession; joined: boolean }> {
  if (!rtdb) throw new Error('Online play is not available right now');
  const db = rtdb;
  const user = auth.currentUser ?? (await signInPlayerAnonymously());
  const uid = user.uid;

  // 1) Try to join someone who is already waiting (closest rating first, then oldest).
  const snap = await get(ref(db, QUEUE_PATH));
  const now = Date.now();
  const waiting = Object.entries((snap.val() ?? {}) as Record<string, QueueEntry>)
    .filter(([, e]) => e && e.host !== uid && !e.claimedBy && now - (e.createdAt ?? 0) < STALE_MS)
    .sort(
      ([, a], [, b]) =>
        Math.abs((a.rating ?? 1000) - rating) - Math.abs((b.rating ?? 1000) - rating) || a.createdAt - b.createdAt,
    );

  for (const [code] of waiting) {
    const res = await runTransaction(ref(db, `${QUEUE_PATH}/${code}`), (cur: QueueEntry | null) => {
      if (!cur) return cur;
      if (cur.claimedBy || cur.host === uid) return undefined; // taken: abort
      return { ...cur, claimedBy: uid };
    });
    const claimed = res.snapshot.val() as QueueEntry | null;
    if (!res.committed || !claimed || claimed.claimedBy !== uid) continue;
    try {
      const session = await OnlineRoomSession.joinRoom(code, callbacks, characterId, displayName);
      void update(ref(db, `rooms/${code}/players/${session.room.localUid}`), { rating }).catch(() => {});
      void remove(ref(db, `${QUEUE_PATH}/${code}`)).catch(() => {});
      return { session, joined: true };
    } catch {
      void remove(ref(db, `${QUEUE_PATH}/${code}`)).catch(() => {});
      // That room is gone: try the next waiting player.
    }
  }

  // 2) Nobody waiting: open a room and wait to be found.
  const session = await OnlineRoomSession.createRoom(callbacks, characterId, displayName);
  const code = session.room.roomCode;
  const entryRef = ref(db, `${QUEUE_PATH}/${code}`);
  await set(entryRef, {
    host: session.room.localUid,
    name: (displayName || 'Player').slice(0, 14),
    characterId,
    rating,
    createdAt: Date.now(),
  });
  void onDisconnect(entryRef).remove();
  void update(ref(db, `rooms/${code}/players/${session.room.localUid}`), { rating }).catch(() => {});
  return { session, joined: false };
}

/** Remove a waiting entry (e.g. when the host leaves before anyone joins). */
export async function leaveQueue(roomCode: string): Promise<void> {
  if (!rtdb) return;
  await remove(ref(rtdb, `${QUEUE_PATH}/${roomCode}`)).catch(() => {});
}

/** Live name / hero / rating of the other player in an Online room. */
export function watchOpponentProfile(
  roomCode: string,
  myUid: string,
  cb: (p: OpponentProfile | null) => void,
): () => void {
  if (!rtdb) return () => {};
  return onValue(ref(rtdb, `rooms/${roomCode}/players`), (snap) => {
    const val = (snap.val() ?? {}) as Record<
      string,
      { uid: string; name?: string; characterId?: CharacterId; rating?: number }
    >;
    const opp = Object.values(val).find((p) => p && p.uid !== myUid);
    cb(
      opp
        ? {
            name: opp.name ?? null,
            characterId: opp.characterId ?? null,
            rating: typeof opp.rating === 'number' ? opp.rating : null,
          }
        : null,
    );
  });
}
