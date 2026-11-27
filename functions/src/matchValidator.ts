export type MatchRecordMode = 'SOLO_1V1' | 'SOLO_2V2' | 'SINGLES_1V1' | 'DOUBLES_2V2';
export type ScoringRuleMode = 'RALLY' | 'SIDE_OUT';
export type WinnerSide = 'LOWER' | 'UPPER';

export interface SubmittedMatchPayload {
  matchId?: string;
  roomCode?: string;
  mode: MatchRecordMode;
  scoringMode: ScoringRuleMode;
  createdByUid: string;
  participants: string[];
  scoreLower: number;
  scoreUpper: number;
  winnerSide: WinnerSide;
  bestRally: number;
  playerCharacter: string;
  partnerCharacter?: string;
  opponentCharacter: string;
}

export interface MatchValidationOutcome {
  isValid: boolean;
  reason: string;
  isOnline: boolean;
  winnerRatingDelta: number;
  loserRatingDelta: number;
}

const VALID_MODES = new Set<string>(['SOLO_1V1', 'SOLO_2V2', 'SINGLES_1V1', 'DOUBLES_2V2']);
const VALID_SCORING = new Set<string>(['RALLY', 'SIDE_OUT']);
const VALID_CHARS = new Set<string>([
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
]);
const UID_REGEX = /^[a-zA-Z0-9_-]{1,128}$/;

export function validateMatchPayload(payload: SubmittedMatchPayload): MatchValidationOutcome {
  if (!VALID_MODES.has(payload.mode)) {
    return invalid('Unsupported match mode');
  }
  if (!VALID_SCORING.has(payload.scoringMode)) {
    return invalid('Unsupported scoring mode');
  }
  if (!UID_REGEX.test(payload.createdByUid)) {
    return invalid('Invalid creator UID');
  }
  if (!Array.isArray(payload.participants) || payload.participants.length === 0 || payload.participants.length > 4) {
    return invalid('Participants must have 1 to 4 entries');
  }
  const uniqueUids = new Set(payload.participants);
  if (uniqueUids.size !== payload.participants.length) {
    return invalid('Duplicate participant UIDs are not allowed');
  }
  for (const uid of payload.participants) {
    if (typeof uid !== 'string' || !UID_REGEX.test(uid)) {
      return invalid('Invalid participant UID format');
    }
  }
  if (!uniqueUids.has(payload.createdByUid)) {
    return invalid('Creator UID must be included in participants');
  }
  if (
    !VALID_CHARS.has(payload.playerCharacter) ||
    !VALID_CHARS.has(payload.opponentCharacter) ||
    (payload.partnerCharacter !== undefined && !VALID_CHARS.has(payload.partnerCharacter))
  ) {
    return invalid('Unknown cosmetic character ID');
  }
  if (
    !Number.isInteger(payload.scoreLower) ||
    !Number.isInteger(payload.scoreUpper) ||
    payload.scoreLower < 0 ||
    payload.scoreUpper < 0 ||
    payload.scoreLower > 99 ||
    payload.scoreUpper > 99
  ) {
    return invalid('Scores must be integers between 0 and 99');
  }
  const high = Math.max(payload.scoreLower, payload.scoreUpper);
  const margin = Math.abs(payload.scoreLower - payload.scoreUpper);
  if (high < 11 || margin < 2) {
    return invalid('Finished match must reach 11 points and win by 2');
  }
  const expectedWinner: WinnerSide = payload.scoreLower > payload.scoreUpper ? 'LOWER' : 'UPPER';
  if (payload.winnerSide !== expectedWinner) {
    return invalid('winnerSide does not match final scores');
  }
  if (!Number.isInteger(payload.bestRally) || payload.bestRally < 0 || payload.bestRally > 999) {
    return invalid('bestRally out of bounds');
  }

  const isOnline = payload.mode === 'SINGLES_1V1' || payload.mode === 'DOUBLES_2V2';
  return {
    isValid: true,
    reason: 'Validated',
    isOnline,
    winnerRatingDelta: isOnline ? 24 : 10,
    loserRatingDelta: isOnline ? -12 : -4,
  };
}

export function nextPlayerRating(currentRating: number, delta: number): number {
  const safeBase = Number.isFinite(currentRating) ? currentRating : 1000;
  return Math.max(800, Math.round(safeBase + delta));
}

function invalid(reason: string): MatchValidationOutcome {
  return {
    isValid: false,
    reason,
    isOnline: false,
    winnerRatingDelta: 0,
    loserRatingDelta: 0,
  };
}
