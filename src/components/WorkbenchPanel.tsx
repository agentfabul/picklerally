import { useMemo, useState } from 'react';
import { Check, Copy, FileCode2, Layers, ShieldCheck } from 'lucide-react';
import { runBallPhysicsTests, runHitDiagnosticsTests, runPhase5PolishTests } from '../game/ballPhysics';
import { runCourtSpecTests } from '../game/courtSpec';
import { runDoublesMatchTests } from '../game/doublesMatch';
import { firebaseProjectConfig } from '../game/firebaseClient';
import { runPhase6StatsTests } from '../game/matchValidator';
import { runOnline1v1Tests } from '../game/onlineMatch1v1';
import { PROJECT_FILES, type ModuleGroup } from '../game/projectFiles';
import { runRulesEngineTests } from '../game/rulesEngine';

type WorkbenchTab = 'files' | 'architecture' | 'checks';

const MODULE_ORDER: { id: ModuleGroup; label: string }[] = [
  { id: 'core', label: 'core/' },
  { id: 'android', label: 'android/' },
  { id: 'desktop', label: 'desktop/' },
  { id: 'functions', label: 'functions/' },
  { id: 'root', label: 'gradle & firebase' },
];

const PHASES = [
  { phase: 1, title: 'Project setup & empty court screen', status: 'Completed' },
  { phase: 2, title: 'Single-player movement, swipe-to-hit, 3D ball physics & bot', status: 'Completed' },
  { phase: 3, title: 'Pure Kotlin rules engine & unit tests', status: 'Completed' },
  { phase: 4, title: 'Firebase Auth & Realtime Database 1v1 rooms', status: 'Completed' },
  { phase: 5, title: 'Character select, menus, audio & fault callouts', status: 'Completed' },
  { phase: 6, title: 'Doubles (2v2), Firestore profiles, history, leaderboard & Cloud Functions', status: 'Ready for review' },
  { phase: 7, title: 'App Check, R8/ProGuard & Play Store closed testing', status: 'Pending' },
];

export function WorkbenchPanel() {
  const [activeTab, setActiveTab] = useState<WorkbenchTab>('files');
  const [selectedPath, setSelectedPath] = useState<string>(PROJECT_FILES[0].path);
  const [copiedPath, setCopiedPath] = useState<string | null>(null);

  const selectedFile = useMemo(
    () => PROJECT_FILES.find((f) => f.path === selectedPath) ?? PROJECT_FILES[0],
    [selectedPath],
  );

  const testResults = useMemo(
    () => [
      ...runPhase6StatsTests(),
      ...runDoublesMatchTests(),
      ...runPhase5PolishTests(),
      ...runOnline1v1Tests(),
      ...runRulesEngineTests(),
      ...runBallPhysicsTests(),
      ...runHitDiagnosticsTests(),
      ...runCourtSpecTests(),
    ],
    [],
  );
  const passedCount = testResults.filter((r) => r.passed).length;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(selectedFile.content);
      setCopiedPath(selectedFile.path);
      setTimeout(() => setCopiedPath(null), 1500);
    } catch {
      // Clipboard blocked in non-HTTPS contexts; ignore silently
    }
  };

  return (
    <section className="flex h-full min-h-0 flex-col overflow-hidden rounded-2xl border border-slate-800 bg-[#121C30]">
      {/* Workbench Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 px-5 py-4">
        <div>
          <h1 className="font-display text-lg font-bold text-white tracking-tight">
            Pickle Rally · Phase 6 Build
          </h1>
          <p className="mt-0.5 text-xs text-slate-400">
            2v2 Doubles (0-0-2 Rotation) · Firestore Profiles &amp; History · Leaderboard · Cloud Function Validator
          </p>
        </div>

        <div
          role="tablist"
          aria-label="Phase 6 inspection views"
          className="flex items-center gap-1 rounded-lg bg-[#0B1220] p-1 border border-slate-800"
        >
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'files'}
            onClick={() => setActiveTab('files')}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors duration-150 whitespace-nowrap shrink-0 cursor-pointer ${
              activeTab === 'files'
                ? 'bg-[#1E293B] text-white'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <FileCode2 className="h-3.5 w-3.5" />
            <span>Source Files</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'architecture'}
            onClick={() => setActiveTab('architecture')}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors duration-150 whitespace-nowrap shrink-0 cursor-pointer ${
              activeTab === 'architecture'
                ? 'bg-[#1E293B] text-white'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Layers className="h-3.5 w-3.5" />
            <span>Architecture</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'checks'}
            onClick={() => setActiveTab('checks')}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors duration-150 whitespace-nowrap shrink-0 cursor-pointer ${
              activeTab === 'checks'
                ? 'bg-[#1E293B] text-white'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <ShieldCheck className="h-3.5 w-3.5" />
            <span className="tabular-nums">Checks ({passedCount}/{testResults.length})</span>
          </button>
        </div>
      </div>

      {/* Tab 1: Real Source File Explorer */}
      {activeTab === 'files' && (
        <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[240px_1fr]">
          <div className="overflow-y-auto border-b border-slate-800 p-3 lg:border-b-0 lg:border-r">
            {MODULE_ORDER.map((group) => {
              const files = PROJECT_FILES.filter((f) => f.module === group.id);
              return (
                <div key={group.id} className="mb-4 last:mb-0">
                  <div className="px-2 pb-1 text-[11px] font-semibold text-slate-400">
                    {group.label}
                  </div>
                  <div className="flex flex-col gap-0.5">
                    {files.map((file) => {
                      const active = file.path === selectedFile.path;
                      return (
                        <button
                          key={file.path}
                          type="button"
                          onClick={() => setSelectedPath(file.path)}
                          className={`w-full rounded-md px-2.5 py-1.5 text-left text-xs font-mono transition-colors duration-150 truncate cursor-pointer ${
                            active
                              ? 'bg-[#1D56B8]/30 text-[#84CC16] font-medium'
                              : 'text-slate-300 hover:bg-slate-800/70 hover:text-white'
                          }`}
                          title={file.path}
                        >
                          {file.name}
                        </button>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>

          <div className="flex min-h-0 flex-col bg-[#0B1220]">
            <div className="flex items-center justify-between gap-4 border-b border-slate-800 px-4 py-2.5">
              <div className="min-w-0">
                <div className="truncate font-mono text-xs text-white">
                  {selectedFile.path}
                </div>
                <div className="truncate text-[11px] text-slate-400">
                  {selectedFile.summary}
                </div>
              </div>
              <button
                type="button"
                onClick={handleCopy}
                className="flex items-center gap-1.5 rounded-md border border-slate-700 bg-[#121C30] px-2.5 py-1.5 text-xs font-medium text-slate-200 transition-colors duration-150 hover:bg-slate-800 whitespace-nowrap shrink-0 cursor-pointer"
              >
                {copiedPath === selectedFile.path ? (
                  <>
                    <Check className="h-3.5 w-3.5 text-[#84CC16]" />
                    <span>Copied</span>
                  </>
                ) : (
                  <>
                    <Copy className="h-3.5 w-3.5" />
                    <span>Copy</span>
                  </>
                )}
              </button>
            </div>

            <div className="min-h-0 flex-1 overflow-auto p-4">
              <pre className="font-mono text-xs leading-6 text-slate-200">
                <code>{selectedFile.content}</code>
              </pre>
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Firebase RTDB Schema, Console Setup & Phase Roadmap */}
      {activeTab === 'architecture' && (
        <div className="min-h-0 flex-1 overflow-y-auto p-6 space-y-8">
          <div>
            <h2 className="font-display text-sm font-bold text-white">
              Phase 6 Doubles (2v2), Firestore Stats &amp; Cloud Functions
            </h2>
            <p className="mt-1 text-xs text-slate-400">
              GCP Project: <span className="font-mono text-slate-200">{firebaseProjectConfig.projectId}</span> · Deployed rules enforce participant-scoped match history &amp; non-PII public rankings
            </p>

            <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="rounded-xl border border-slate-800 bg-[#0B1220] p-4">
                <div className="font-mono text-xs font-semibold text-[#84CC16]">
                  2v2 Doubles Rotation &amp; Side-Switching
                </div>
                <p className="mt-1.5 text-xs text-slate-300 leading-relaxed">
                  DoublesMatch starts at 0-0-2, swaps teammate court boxes on each serving-team point, alternates Server 1 and Server 2 before side-out, and drives AI partner &amp; dual AI opponents under full two-bounce and NVZ rules.
                </p>
              </div>
              <div className="rounded-xl border border-slate-800 bg-[#0B1220] p-4">
                <div className="font-mono text-xs font-semibold text-[#F97316]">
                  Cloud Function Validation &amp; Leaderboard
                </div>
                <p className="mt-1.5 text-xs text-slate-300 leading-relaxed">
                  validateMatchOnCreate verifies 11+ win-by-2 scores, participant membership, and character IDs before atomically updating /users/&#123;uid&#125; career stats and non-PII /leaderboard/&#123;uid&#125; ELO ratings.
                </p>
              </div>
            </div>
          </div>

          <div className="border-t border-slate-800 pt-6">
            <h2 className="font-display text-sm font-bold text-white">
              Delivery Phases
            </h2>
            <div className="mt-3 divide-y divide-slate-800/80 rounded-xl border border-slate-800 bg-[#0B1220]">
              {PHASES.map((item) => {
                const isCurrent = item.phase === 6;
                const isDone = item.phase < 6;
                return (
                  <div
                    key={item.phase}
                    className="flex items-center justify-between gap-4 px-4 py-3 text-xs"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <span className="font-mono tabular-nums text-slate-400 shrink-0">
                        0{item.phase}
                      </span>
                      <span className={isCurrent ? 'font-semibold text-white truncate' : 'text-slate-300 truncate'}>
                        {item.title}
                      </span>
                    </div>
                    <span
                      className={`shrink-0 font-medium ${
                        isCurrent
                          ? 'text-[#84CC16]'
                          : isDone
                            ? 'text-slate-300'
                            : 'text-slate-500'
                      }`}
                    >
                      {item.status}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Tab 3: All Online 1v1, Rules, Physics & Geometry Checks */}
      {activeTab === 'checks' && (
        <div className="min-h-0 flex-1 overflow-y-auto p-6 space-y-6">
          <div>
            <div className="flex items-center justify-between gap-4">
              <h2 className="font-display text-sm font-bold text-white">
                Doubles 2v2, Cloud Validator, Polish, Online 1v1, Rules, Physics &amp; Court Assertions
              </h2>
              <span className="font-mono text-xs tabular-nums text-[#84CC16]">
                {passedCount} / {testResults.length} Passed
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-400">
              Live browser verification mirroring DoublesMatchTest.kt, Phase5PolishTest.kt, OnlineMatch1v1Test.kt, RulesEngineTest.kt, BallPhysicsTest.kt, and CourtSpecTest.kt in core/src/test/.
            </p>

            <div className="mt-3 divide-y divide-slate-800 rounded-xl border border-slate-800 bg-[#0B1220]">
              {testResults.map((res) => (
                <div key={res.id} className="flex items-start justify-between gap-4 p-4">
                  <div>
                    <div className="text-xs font-medium text-white">{res.name}</div>
                    <div className="mt-0.5 text-[11px] text-slate-400">{res.detail}</div>
                  </div>
                  <span
                    className={`font-mono text-xs font-semibold shrink-0 ${
                      res.passed ? 'text-[#84CC16]' : 'text-rose-400'
                    }`}
                  >
                    {res.passed ? 'PASS' : 'FAIL'}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div className="border-t border-slate-800 pt-6">
            <h2 className="font-display text-sm font-bold text-white">
              Local Gradle Verification Commands
            </h2>
            <div className="mt-3 space-y-2 rounded-xl border border-slate-800 bg-[#0B1220] p-4 font-mono text-xs text-slate-200">
              <div>./gradlew :core:test</div>
              <div>./gradlew :desktop:run</div>
              <div>./gradlew :android:assembleDebug</div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
