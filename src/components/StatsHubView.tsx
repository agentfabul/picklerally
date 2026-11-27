import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, Award, Check, History, RefreshCw, Trophy, UserCheck } from 'lucide-react';
import type { User } from 'firebase/auth';
import { CHARACTER_PALETTES, type CharacterId } from '../game/ballPhysics';
import {
  fetchLeaderboard,
  fetchMatchHistory,
  fetchUserProfile,
  updateUserDisplayName,
  type LeaderboardRow,
  type MatchHistoryItem,
  type UserProfileStats,
} from '../game/firebaseClient';
import { soundFx } from '../game/soundFx';
import { CharacterPortrait } from './CharacterPortrait';

type HubTab = 'PROFILE' | 'HISTORY' | 'LEADERBOARD';

interface StatsHubViewProps {
  user: User | null;
  playerChar: CharacterId;
  onBack: () => void;
}

export function StatsHubView({ user, playerChar, onBack }: StatsHubViewProps) {
  const [tab, setTab] = useState<HubTab>('PROFILE');
  const [profile, setProfile] = useState<UserProfileStats | null>(null);
  const [history, setHistory] = useState<MatchHistoryItem[]>([]);
  const [leaderboard, setLeaderboard] = useState<LeaderboardRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const [nameInput, setNameInput] = useState('');
  const [nameSaving, setNameSaving] = useState(false);
  const [nameSavedBanner, setNameSavedBanner] = useState(false);

  const loadAll = useCallback(async () => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const [prof, hist, lb] = await Promise.all([
        fetchUserProfile(user, playerChar),
        fetchMatchHistory(user),
        fetchLeaderboard(user, playerChar),
      ]);
      setProfile(prof);
      setNameInput(prof.displayName);
      setHistory(hist);
      setLeaderboard(lb);
    } catch (err) {
      setErrorMsg(
        err instanceof Error
          ? 'Could not sync all cloud records; check your Firebase sign-in.'
          : 'Failed to load player stats.',
      );
    } finally {
      setLoading(false);
    }
  }, [user, playerChar]);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  const handleSaveName = async () => {
    const trimmed = nameInput.trim();
    if (!trimmed) {
      setErrorMsg('Display name cannot be empty.');
      return;
    }
    setNameSaving(true);
    setErrorMsg(null);
    try {
      const saved = await updateUserDisplayName(user, trimmed, playerChar);
      setProfile((prev) => (prev ? { ...prev, displayName: saved } : prev));
      setNameSavedBanner(true);
      soundFx.playMenuClick();
      setTimeout(() => setNameSavedBanner(false), 1600);
      const lb = await fetchLeaderboard(user, playerChar);
      setLeaderboard(lb);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Could not update display name.');
    } finally {
      setNameSaving(false);
    }
  };

  const winRate =
    profile && profile.matchesPlayed > 0
      ? Math.round((profile.wins / profile.matchesPlayed) * 100)
      : 0;

  return (
    <div className="flex h-full w-full flex-col bg-[#0E172A] p-4 text-slate-100">
      {/* Header */}
      <div className="flex items-center justify-between gap-2 border-b border-slate-800 pb-3">
        <button
          type="button"
          onClick={() => {
            soundFx.playMenuClick();
            onBack();
          }}
          className="flex items-center gap-1.5 rounded-lg border border-slate-700 bg-[#121C30] px-2.5 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-800 whitespace-nowrap shrink-0 cursor-pointer"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          <span>Back</span>
        </button>

        <div className="text-center">
          <h2 className="font-display text-sm font-bold text-white">Player Stats &amp; Ranks</h2>
          <div className="text-[10px] text-slate-400">
            {user ? 'Firestore Cloud Profile' : 'Local Profile (Sign in to sync)'}
          </div>
        </div>

        <button
          type="button"
          onClick={() => void loadAll()}
          disabled={loading}
          aria-label="Refresh stats"
          title="Refresh"
          className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-700 bg-[#121C30] text-slate-300 hover:bg-slate-800 disabled:opacity-50 cursor-pointer"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {/* Segmented Tabs */}
      <div
        role="tablist"
        aria-label="Player hub sections"
        className="mt-3 grid grid-cols-3 gap-1 rounded-xl border border-slate-800 bg-[#0B1220] p-1"
      >
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'PROFILE'}
          onClick={() => {
            soundFx.playMenuClick();
            setTab('PROFILE');
          }}
          className={`flex items-center justify-center gap-1 rounded-lg py-1.5 text-[11px] font-semibold whitespace-nowrap shrink-0 cursor-pointer ${
            tab === 'PROFILE' ? 'bg-[#1E293B] text-white' : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          <UserCheck className="h-3 w-3" />
          <span>Profile</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'HISTORY'}
          onClick={() => {
            soundFx.playMenuClick();
            setTab('HISTORY');
          }}
          className={`flex items-center justify-center gap-1 rounded-lg py-1.5 text-[11px] font-semibold whitespace-nowrap shrink-0 cursor-pointer ${
            tab === 'HISTORY' ? 'bg-[#1E293B] text-white' : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          <History className="h-3 w-3" />
          <span>History</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'LEADERBOARD'}
          onClick={() => {
            soundFx.playMenuClick();
            setTab('LEADERBOARD');
          }}
          className={`flex items-center justify-center gap-1 rounded-lg py-1.5 text-[11px] font-semibold whitespace-nowrap shrink-0 cursor-pointer ${
            tab === 'LEADERBOARD'
              ? 'bg-[#1E293B] text-[#84CC16]'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          <Trophy className="h-3 w-3" />
          <span>Ranks</span>
        </button>
      </div>

      {errorMsg && (
        <div className="mt-2.5 rounded-lg border border-rose-500/40 bg-rose-950/50 px-3 py-2 text-[11px] text-rose-200">
          {errorMsg}
        </div>
      )}

      {/* Tab Content */}
      <div className="mt-3 min-h-0 flex-1 overflow-y-auto pr-0.5">
        {tab === 'PROFILE' && profile && (
          <div className="space-y-3">
            <div className="rounded-xl border border-slate-800 bg-[#121C30] p-3.5">
              <div className="flex items-center gap-3">
                <CharacterPortrait characterId={profile.characterId} size={52} />
                <div className="min-w-0 flex-1">
                  <div className="text-[10px] font-mono uppercase tracking-wider text-slate-400">
                    Active Fighter · {CHARACTER_PALETTES[profile.characterId].name}
                  </div>
                  <div className="mt-0.5 truncate font-display text-base font-bold text-white">
                    {profile.displayName}
                  </div>
                  <div className="font-mono text-xs tabular-nums text-[#84CC16]">
                    Rating {profile.rating}
                  </div>
                </div>
              </div>

              <div className="mt-3 flex items-center gap-1.5">
                <input
                  type="text"
                  value={nameInput}
                  maxLength={32}
                  onChange={(e) => setNameInput(e.target.value)}
                  aria-label="Player display name"
                  placeholder="Player name"
                  className="min-w-0 flex-1 rounded-lg border border-slate-700 bg-[#0B1220] px-2.5 py-1.5 text-xs text-white placeholder:text-slate-500 focus:border-[#84CC16] focus:outline-none"
                />
                <button
                  type="button"
                  disabled={nameSaving}
                  onClick={() => void handleSaveName()}
                  className="flex items-center gap-1 rounded-lg bg-[#84CC16] px-3 py-1.5 text-xs font-bold text-[#0B1220] hover:bg-[#95DF26] disabled:opacity-50 whitespace-nowrap shrink-0 cursor-pointer"
                >
                  {nameSavedBanner ? <Check className="h-3.5 w-3.5" /> : null}
                  <span>{nameSavedBanner ? 'Saved' : 'Save'}</span>
                </button>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div className="rounded-xl border border-slate-800 bg-[#121C30] p-3">
                <div className="text-[11px] text-slate-400">Record (W – L)</div>
                <div className="mt-1 font-mono text-lg font-bold tabular-nums text-white">
                  {profile.wins} – {profile.losses}
                </div>
              </div>
              <div className="rounded-xl border border-slate-800 bg-[#121C30] p-3">
                <div className="text-[11px] text-slate-400">Win Rate</div>
                <div className="mt-1 font-mono text-lg font-bold tabular-nums text-[#84CC16]">
                  {winRate}%
                </div>
              </div>
              <div className="rounded-xl border border-slate-800 bg-[#121C30] p-3">
                <div className="text-[11px] text-slate-400">Best Rally</div>
                <div className="mt-1 font-mono text-lg font-bold tabular-nums text-white">
                  {profile.bestRally} shots
                </div>
              </div>
              <div className="rounded-xl border border-slate-800 bg-[#121C30] p-3">
                <div className="text-[11px] text-slate-400">Points Scored</div>
                <div className="mt-1 font-mono text-lg font-bold tabular-nums text-[#F97316]">
                  {profile.pointsScored}
                </div>
              </div>
            </div>

            <div className="rounded-xl border border-slate-800 bg-[#0B1220] p-3 text-[11px] leading-relaxed text-slate-400">
              Completed 1v1 and 2v2 matches save to Firestore <span className="font-mono text-slate-300">/matches</span> where the <span className="font-mono text-slate-300">validateMatchOnCreate</span> Cloud Function verifies scores and updates rankings.
            </div>
          </div>
        )}

        {tab === 'HISTORY' && (
          <div className="space-y-2">
            {history.length === 0 ? (
              <div className="rounded-xl border border-slate-800 bg-[#121C30] p-6 text-center text-xs text-slate-400">
                No finished matches recorded yet. Finish a 1v1 or 2v2 game to log it here.
              </div>
            ) : (
              history.map((item) => {
                const won = item.winnerSide === 'LOWER';
                const modeLabel =
                  item.mode === 'DOUBLES_2V2'
                    ? 'Online 2v2'
                    : item.mode === 'SOLO_2V2'
                      ? '2v2 Doubles'
                      : item.mode === 'SINGLES_1V1'
                        ? 'Online 1v1'
                        : 'Solo 1v1';
                const statusLabel =
                  item.source === 'LOCAL'
                    ? 'Local'
                    : item.validated
                      ? 'Verified'
                      : 'Pending Cloud Check';

                return (
                  <div
                    key={item.matchId}
                    className="flex items-center justify-between gap-2 rounded-xl border border-slate-800 bg-[#121C30] p-3"
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <CharacterPortrait characterId={item.opponentCharacter} size={36} />
                      <div className="min-w-0">
                        <div className="flex items-center gap-1.5 text-xs font-bold text-white">
                          <span className={won ? 'text-[#84CC16]' : 'text-rose-300'}>
                            {won ? 'Win' : 'Loss'}
                          </span>
                          <span className="text-slate-500">·</span>
                          <span className="truncate">
                            {modeLabel} vs {CHARACTER_PALETTES[item.opponentCharacter].name}
                          </span>
                        </div>
                        <div className="mt-0.5 text-[10px] font-mono text-slate-400">
                          Rally {item.bestRally} · {statusLabel}
                        </div>
                      </div>
                    </div>

                    <div className="text-right font-mono text-sm font-bold tabular-nums text-white shrink-0">
                      {item.scoreLower} – {item.scoreUpper}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}

        {tab === 'LEADERBOARD' && (
          <div className="space-y-2">
            {leaderboard.length === 0 ? (
              <div className="rounded-xl border border-slate-800 bg-[#121C30] p-6 text-center text-xs text-slate-400">
                No ranked players on the board yet. Sign in and finish a match to claim #1.
              </div>
            ) : (
              leaderboard.map((row, idx) => {
                const isSelf = user ? row.uid === user.uid : row.uid === 'local-player';
                return (
                  <div
                    key={row.uid}
                    className={`flex items-center justify-between gap-2 rounded-xl border p-2.5 ${
                      isSelf
                        ? 'border-[#84CC16]/60 bg-[#162642]'
                        : 'border-slate-800 bg-[#121C30]'
                    }`}
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div className="w-6 text-center font-mono text-xs font-bold tabular-nums text-slate-400 shrink-0">
                        {idx === 0 ? (
                          <Award className="mx-auto h-4 w-4 text-amber-400" />
                        ) : (
                          `#${idx + 1}`
                        )}
                      </div>
                      <CharacterPortrait characterId={row.characterId} size={32} />
                      <div className="min-w-0">
                        <div className="truncate text-xs font-bold text-white">
                          {row.displayName}
                        </div>
                        <div className="font-mono text-[10px] tabular-nums text-slate-400">
                          {row.wins}W – {row.losses}L · Best {row.bestRally}
                        </div>
                      </div>
                    </div>

                    <div className="text-right font-mono text-xs font-bold tabular-nums text-[#84CC16] shrink-0">
                      {row.rating}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}
      </div>
    </div>
  );
}
