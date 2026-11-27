import { useState } from 'react';
import { Code2, Smartphone } from 'lucide-react';
import { PhoneStage } from './components/PhoneStage';
import { WorkbenchPanel } from './components/WorkbenchPanel';

// Players only see the game. The developer workbench (Source Files / Architecture / Checks)
// shows in local dev, or on the live site when the URL has ?dev (e.g. /?dev).
const SHOW_WORKBENCH: boolean =
  Boolean((import.meta as unknown as { env?: { DEV?: boolean } }).env?.DEV) ||
  (typeof window !== 'undefined' && new URLSearchParams(window.location.search).has('dev'));

export default function App() {
  if (!SHOW_WORKBENCH) {
    return (
      <main className="flex h-[100dvh] w-screen items-center justify-center overflow-hidden bg-[#0B1220] text-[#F8FAFC] sm:p-4">
        <PhoneStage />
      </main>
    );
  }
  return <DevWorkspace />;
}

function DevWorkspace() {
  const [mobileView, setMobileView] = useState<'court' | 'workbench'>('court');

  return (
    <main className="flex h-screen w-screen flex-col overflow-hidden bg-[#0B1220] text-[#F8FAFC]">
      {/* Compact Mobile Switcher (hidden on desktop lg+) */}
      <div className="flex items-center justify-between border-b border-slate-800 px-4 py-2 lg:hidden">
        <span className="font-display text-sm font-bold text-white whitespace-nowrap shrink-0">
          Pickle Rally
        </span>
        <div className="flex items-center gap-1 rounded-lg bg-[#121C30] p-1 border border-slate-800">
          <button
            type="button"
            onClick={() => setMobileView('court')}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer ${
              mobileView === 'court' ? 'bg-[#1E293B] text-white' : 'text-slate-400'
            }`}
          >
            <Smartphone className="h-3.5 w-3.5" />
            <span>Court</span>
          </button>
          <button
            type="button"
            onClick={() => setMobileView('workbench')}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer ${
              mobileView === 'workbench' ? 'bg-[#1E293B] text-white' : 'text-slate-400'
            }`}
          >
            <Code2 className="h-3.5 w-3.5" />
            <span>Project</span>
          </button>
        </div>
      </div>

      {/* Main Split Workspace: Portrait Game Stage + Phase 1 Module Workbench */}
      <div className="mx-auto grid min-h-0 w-full max-w-[1440px] flex-1 grid-cols-1 gap-6 p-4 lg:grid-cols-[auto_1fr] lg:p-6">
        <div
          className={`flex min-h-0 items-center justify-center ${
            mobileView === 'court' ? 'flex' : 'hidden lg:flex'
          }`}
        >
          <PhoneStage />
        </div>

        <div
          className={`min-h-0 ${
            mobileView === 'workbench' ? 'block' : 'hidden lg:block'
          }`}
        >
          <WorkbenchPanel />
        </div>
      </div>
    </main>
  );
}
