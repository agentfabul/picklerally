import { getApp, getApps, initializeApp } from 'firebase-admin/app';
import { getDatabase } from 'firebase-admin/database';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import { nextPlayerRating, validateMatchPayload, type SubmittedMatchPayload } from './matchValidator';

const FIRESTORE_DATABASE_ID = 'ai-studio-picklerally-f6f8fee0-5f87-4cbb-863e-127c5efc486f';
const app = getApps().length ? getApp() : initializeApp();

const db = getFirestore(app, FIRESTORE_DATABASE_ID);
const rtdb = getDatabase(app);

/**
 * Triggered whenever a finished match record is written to Firestore `/matches/{matchId}`.
 * Validates official pickleball win conditions (11+ pts, win by 2), stamps `validated: true`,
 * and atomically updates `/users/{uid}` + `/leaderboard/{uid}` stats inside a transaction.
 */
export const validateMatchOnCreate = onDocumentCreated(
  { document: 'matches/{matchId}', database: FIRESTORE_DATABASE_ID },
  async (event) => {
  const snap = event.data;
  if (!snap) return;

  const raw = snap.data() as SubmittedMatchPayload & {
    validated?: boolean;
    playerDisplayName?: string;
  };
  if (raw.validated === true) return;

  const outcome = validateMatchPayload(raw);
  const matchRef = snap.ref;

  if (!outcome.isValid) {
    await matchRef.update({
      validated: false,
      validationError: outcome.reason,
      validatedAt: FieldValue.serverTimestamp(),
    });
    return;
  }

  await db.runTransaction(async (tx) => {
    const freshSnap = await tx.get(matchRef);
    if (!freshSnap.exists || freshSnap.data()?.validated === true) {
      return;
    }

    const uid = raw.createdByUid;
    const userRef = db.collection('users').doc(uid);
    const lbRef = db.collection('leaderboard').doc(uid);

    const [userSnap, lbSnap] = await Promise.all([tx.get(userRef), tx.get(lbRef)]);
    const didLowerWin = raw.winnerSide === 'LOWER';
    const ratingDelta = didLowerWin ? outcome.winnerRatingDelta : outcome.loserRatingDelta;

    const currentRating =
      (lbSnap.data()?.rating as number | undefined) ??
      (userSnap.data()?.rating as number | undefined) ??
      1000;
    const nextRating = nextPlayerRating(currentRating, ratingDelta);
    const prevWins = (userSnap.data()?.wins as number | undefined) ?? 0;
    const prevLosses = (userSnap.data()?.losses as number | undefined) ?? 0;
    const prevPoints = (userSnap.data()?.pointsScored as number | undefined) ?? 0;
    const prevBestRally = (userSnap.data()?.bestRally as number | undefined) ?? 0;

    const nextWins = prevWins + (didLowerWin ? 1 : 0);
    const nextLosses = prevLosses + (didLowerWin ? 0 : 1);
    const nextMatches = nextWins + nextLosses;
    const nextPoints = prevPoints + raw.scoreLower;
    const nextBestRally = Math.max(prevBestRally, raw.bestRally);
    const displayName =
      (userSnap.data()?.displayName as string | undefined) ||
      raw.playerDisplayName ||
      `Player-${uid.slice(0, 5)}`;

    tx.update(matchRef, {
      validated: true,
      validationError: FieldValue.delete(),
      validatedAt: FieldValue.serverTimestamp(),
    });

    tx.set(
      userRef,
      {
        uid,
        displayName: displayName.slice(0, 32),
        characterId: raw.playerCharacter,
        wins: nextWins,
        losses: nextLosses,
        matchesPlayed: nextMatches,
        pointsScored: nextPoints,
        bestRally: nextBestRally,
        rating: nextRating,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );

    tx.set(
      lbRef,
      {
        uid,
        displayName: displayName.slice(0, 32),
        characterId: raw.playerCharacter,
        wins: nextWins,
        losses: nextLosses,
        matchesPlayed: nextMatches,
        pointsScored: nextPoints,
        bestRally: nextBestRally,
        rating: nextRating,
        isPublic: true,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
  });
  },
);

/**
 * Callable endpoint for online 1v1 & 2v2 rooms: cross-checks live RTDB `/rooms/{roomCode}/state`
 * authoritative host state against client submission before recording the validated match.
 */
export const submitOnlineMatchResult = onCall(async (request) => {
  if (!request.auth?.uid) {
    throw new HttpsError('unauthenticated', 'Authentication required to submit match results.');
  }

  const data = request.data as SubmittedMatchPayload & { roomCode: string };
  if (!data?.roomCode || !/^PKL-[0-9]{3}$/.test(data.roomCode)) {
    throw new HttpsError('invalid-argument', 'Valid PKL-### room code is required.');
  }

  const stateSnap = await rtdb.ref(`rooms/${data.roomCode}/state`).get();
  if (!stateSnap.exists()) {
    throw new HttpsError('not-found', `Room ${data.roomCode} state not found.`);
  }

  const rtdbState = stateSnap.val() as {
    scoreHost?: number;
    scoreGuest?: number;
    matchWinner?: string | null;
  };
  if (
    rtdbState.scoreHost !== data.scoreLower ||
    rtdbState.scoreGuest !== data.scoreUpper ||
    !rtdbState.matchWinner
  ) {
    throw new HttpsError(
      'failed-precondition',
      'Submitted score does not match authoritative RTDB room state.',
    );
  }

  const payload: SubmittedMatchPayload = {
    ...data,
    createdByUid: request.auth.uid,
  };
  const outcome = validateMatchPayload(payload);
  if (!outcome.isValid) {
    throw new HttpsError('invalid-argument', outcome.reason);
  }

  const matchId = `${data.roomCode}-${Date.now()}`;
  await db.collection('matches').doc(matchId).set({
    matchId,
    roomCode: data.roomCode,
    mode: payload.mode,
    scoringMode: payload.scoringMode,
    createdByUid: payload.createdByUid,
    participants: payload.participants,
    scoreLower: payload.scoreLower,
    scoreUpper: payload.scoreUpper,
    winnerSide: payload.winnerSide,
    bestRally: payload.bestRally,
    playerCharacter: payload.playerCharacter,
    ...(payload.partnerCharacter ? { partnerCharacter: payload.partnerCharacter } : {}),
    opponentCharacter: payload.opponentCharacter,
    validated: false,
    createdAt: FieldValue.serverTimestamp(),
  });

  return { matchId, status: 'QUEUED_FOR_VALIDATION' };
});
