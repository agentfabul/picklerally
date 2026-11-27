import {
  nextPlayerRating,
  validateMatchPayload,
  type MatchRecordMode,
  type ScoringRuleMode,
  type SubmittedMatchPayload,
  type WinnerSide,
} from '../../functions/src/matchValidator';
import type { SpecTestResult } from './courtSpec';

export {
  nextPlayerRating,
  validateMatchPayload,
  type MatchRecordMode,
  type ScoringRuleMode,
  type SubmittedMatchPayload,
  type WinnerSide,
};

export function runPhase6StatsTests(): SpecTestResult[] {
  const validOnline = validateMatchPayload({
    mode: 'DOUBLES_2V2',
    scoringMode: 'SIDE_OUT',
    createdByUid: 'user-host-1',
    participants: ['user-host-1', 'user-guest-2'],
    scoreLower: 12,
    scoreUpper: 10,
    winnerSide: 'LOWER',
    bestRally: 14,
    playerCharacter: 'RIO',
    opponentCharacter: 'LOLA',
  });

  const forgedWinBy1 = validateMatchPayload({
    mode: 'SINGLES_1V1',
    scoringMode: 'RALLY',
    createdByUid: 'user-host-1',
    participants: ['user-host-1', 'user-guest-2'],
    scoreLower: 11,
    scoreUpper: 10,
    winnerSide: 'LOWER',
    bestRally: 5,
    playerCharacter: 'RIO',
    opponentCharacter: 'LOLA',
  });

  const spoofedCreator = validateMatchPayload({
    mode: 'SOLO_1V1',
    scoringMode: 'RALLY',
    createdByUid: 'spoof-uid',
    participants: ['real-uid'],
    scoreLower: 11,
    scoreUpper: 4,
    winnerSide: 'LOWER',
    bestRally: 8,
    playerCharacter: 'BERT',
    opponentCharacter: 'DILL',
  });

  const soloMayaVsDuke = validateMatchPayload({
    mode: 'SOLO_1V1',
    scoringMode: 'RALLY',
    createdByUid: 'user-maya-1',
    participants: ['user-maya-1'],
    scoreLower: 11,
    scoreUpper: 7,
    winnerSide: 'LOWER',
    bestRally: 9,
    playerCharacter: 'MAYA',
    opponentCharacter: 'DUKE',
  });

  const nextWinRating = nextPlayerRating(1000, validOnline.winnerRatingDelta);
  const floorLossRating = nextPlayerRating(805, -20);

  return [
    {
      id: 'fn-validate-win-by-2',
      name: 'Cloud Function validator enforces 11+ pts & win-by-2',
      passed: validOnline.isValid && !forgedWinBy1.isValid,
      detail: `12-10 valid=${validOnline.isValid}, 11-10 rejected="${forgedWinBy1.reason}"`,
    },
    {
      id: 'fn-validate-participants',
      name: 'Cloud Function rejects unlisted creator & calculates clamped ELO',
      passed: !spoofedCreator.isValid && nextWinRating === 1024 && floorLossRating === 800,
      detail: `Spoof rejected="${spoofedCreator.reason}", Rating 1000->${nextWinRating}, Floor=${floorLossRating}`,
    },
    {
      id: 'fn-validate-10-char-roster',
      name: 'Cloud Function accepts new roster characters (MAYA vs DUKE)',
      passed: soloMayaVsDuke.isValid && soloMayaVsDuke.winnerRatingDelta === 10,
      detail: `MAYA vs DUKE 11-7 valid=${soloMayaVsDuke.isValid}, ELO +${soloMayaVsDuke.winnerRatingDelta}`,
    },
  ];
}
