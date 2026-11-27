import { useEffect, useRef, useState, type ReactNode } from 'react';
import { soundFx } from '../game/soundFx';

type LearnTab = 'VIDEO' | 'RULES' | 'CONTROLS' | 'TIPS';

interface LearnItem {
  title: string;
  points: string[];
}

/** Official pickleball rules (USA Pickleball rulebook basics), written for new players. */
const RULES: LearnItem[] = [
  {
    title: '1. The court',
    points: [
      'Court is 20 ft wide and 44 ft long, the same for singles and doubles.',
      'The net is 36 in high at the sidelines and 34 in at the center.',
      'The 7 ft zone on each side of the net is the Non-Volley Zone, called the "kitchen".',
      'Behind the kitchen, each half is split into a right and a left service box.',
    ],
  },
  {
    title: '2. Serving',
    points: [
      'Serve underhand: hit the ball below your waist with an upward arc (or use a drop serve).',
      'Keep both feet behind the baseline until you hit the ball.',
      'Serve diagonally into the opposite service box. It must clear the kitchen.',
      'A serve that lands on the kitchen line is a fault. Landing on any other line of the box is in.',
      'A serve that clips the net and lands in the correct box is in play (no re-serve).',
      'You get one serve attempt per point.',
    ],
  },
  {
    title: '3. Two-bounce rule',
    points: [
      'The return of serve must bounce before it is hit: the receiver cannot volley the serve.',
      'The serving side must also let the return bounce before hitting the 3rd shot.',
      'After those two bounces, either side may volley (hit it in the air) or play it off the bounce.',
    ],
  },
  {
    title: '4. The kitchen (Non-Volley Zone)',
    points: [
      'You may not volley while standing in the kitchen or touching the kitchen line.',
      'If your momentum carries you into the kitchen after a volley, it is still a fault.',
      'You may step into the kitchen at any time to hit a ball that has bounced.',
    ],
  },
  {
    title: '5. Faults (you lose the rally)',
    points: [
      'Hitting the ball out of bounds or into the net.',
      'Volleying before the two-bounce rule allows it.',
      'Volleying from the kitchen.',
      'Letting the ball bounce twice on your side.',
      'Balls touching a line are in (except the kitchen line on a serve).',
    ],
  },
  {
    title: '6. Scoring (traditional / side-out)',
    points: [
      'Only the serving side can score a point.',
      'If the server loses the rally, the serve passes to the opponent ("side out").',
      'Games are usually played to 11 points and must be won by 2 (some to 15 or 21).',
      'Tournament matches are often best of 3 games.',
    ],
  },
  {
    title: '7. Singles serving & score call',
    points: [
      'Serve from the right box when your score is even (0, 2, 4...).',
      'Serve from the left box when your score is odd (1, 3, 5...).',
      'Call the score before serving: server score first, then receiver score, e.g. "4 - 2".',
    ],
  },
  {
    title: '8. Doubles serving',
    points: [
      'Call three numbers: serving team score, receiving team score, server number, e.g. "4 - 2 - 1".',
      'Both partners serve before a side out, except at the start of the game ("0 - 0 - 2").',
      'When the serving team wins a point, the server switches sides with their partner.',
    ],
  },
  {
    title: '9. Rally scoring (variant)',
    points: [
      'A point is scored on every rally, no matter who served.',
      'The winner of the rally serves next. Games still need a 2-point lead.',
    ],
  },
];

const CONTROLS: LearnItem[] = [
  {
    title: 'Move',
    points: [
      'Tap anywhere on your half of the court to run Dill there.',
      'Keyboard: W A S D or the arrow keys.',
      'You have to move to the ball yourself: watch where Lola aims and get there early.',
    ],
  },
  {
    title: 'Hit',
    points: [
      'The ring around Dill is his reach. When the ball is inside it and a hit is legal, he swings a straight drive automatically.',
      'An orange ring means the ball is close but you can\'t hit yet: "Let it bounce" (two-bounce rule), "Step out of the kitchen" (no volleys there) or "Too high".',
      'Swipe on the court to choose and aim your own shot:',
      'Short, slow swipe = Dink (soft shot into the kitchen).',
      'Medium swipe straight up = Lob (high and deep).',
      'Long, fast swipe = Drive (low and hard). Swipe left or right to aim.',
      'Keyboard: 1 = Dink, 2 or Space = Drive, 3 = Lob.',
    ],
  },
  {
    title: 'Serve',
    points: [
      'Press "Serve Diagonal", press Space, or swipe up from Dill when it is your serve.',
      'When Lola serves, wait for the countdown and let her serve bounce before you hit.',
    ],
  },
  {
    title: 'Match options',
    points: [
      'Opponent level: Easy (slow, aims near you, misses more), Normal, Hard (faster, aims wider), Pro (fastest, rarely misses, aims close to the lines).',
      '2v2: your AI partner covers the other half of the court. Balls on your half are yours to play.',
      'Rally or Side-Out scoring.',
      'Games to 11, 15 or 21 (win by 2), as a single game or best of 3.',
      'The badge next to the score shows the game number and games won, e.g. "G2 · 1-0".',
    ],
  },
];

const TIPS: LearnItem[] = [
  {
    title: 'Serve and return deep',
    points: [
      'A deep serve keeps Lola at her baseline.',
      'A deep return gives you time to move up to the kitchen line.',
    ],
  },
  {
    title: 'Get to the kitchen line',
    points: [
      'Most points are won at the net. After the two bounces, move up to just behind the kitchen line.',
      'Stay behind the line when you volley.',
    ],
  },
  {
    title: 'The third-shot drop',
    points: [
      'As the server, your 3rd shot is often a soft dink that lands in Lola\'s kitchen.',
      'It gives you time to move forward while she has to hit up.',
    ],
  },
  {
    title: 'Dink with patience',
    points: [
      'When both players are at the kitchen, trade soft dinks and wait for a high ball.',
      'Attack a ball that pops up with a drive at the opponent\'s feet.',
    ],
  },
  {
    title: 'Watch for lobs',
    points: [
      'If you crowd the net, Lola may lob over you. Back up early when you see a high ball.',
    ],
  },
];

function CourtDiagram() {
  return (
    <svg viewBox="0 0 120 220" className="mx-auto h-40 w-auto" role="img" aria-label="Pickleball court diagram">
      <rect x="10" y="10" width="100" height="200" rx="2" fill="#1D4ED8" stroke="#F8FAFC" strokeWidth="1.5" />
      <rect x="10" y="75" width="100" height="70" fill="#1E40AF" stroke="#F8FAFC" strokeWidth="1" />
      <line x1="60" y1="10" x2="60" y2="75" stroke="#F8FAFC" strokeWidth="1" />
      <line x1="60" y1="145" x2="60" y2="210" stroke="#F8FAFC" strokeWidth="1" />
      <line x1="4" y1="110" x2="116" y2="110" stroke="#F8FAFC" strokeWidth="2.5" />
      <text x="60" y="95" textAnchor="middle" fontSize="8" fill="#FDE68A" fontWeight="700">KITCHEN</text>
      <text x="60" y="131" textAnchor="middle" fontSize="8" fill="#FDE68A" fontWeight="700">KITCHEN</text>
      <text x="60" y="107" textAnchor="middle" fontSize="6" fill="#CBD5E1">net</text>
      <text x="35" y="182" textAnchor="middle" fontSize="7" fill="#E2E8F0">Left</text>
      <text x="85" y="182" textAnchor="middle" fontSize="7" fill="#E2E8F0">Right</text>
      <text x="35" y="42" textAnchor="middle" fontSize="7" fill="#E2E8F0">Right</text>
      <text x="85" y="42" textAnchor="middle" fontSize="7" fill="#E2E8F0">Left</text>
      <path d="M85 200 L35 30" stroke="#84CC16" strokeWidth="1.5" strokeDasharray="4 3" fill="none" />
      <circle cx="85" cy="200" r="3" fill="#84CC16" />
      <text x="60" y="219" textAnchor="middle" fontSize="6.5" fill="#84CC16">serve diagonally, past the kitchen</text>
    </svg>
  );
}

function ItemList({ items }: { items: LearnItem[] }) {
  const [open, setOpen] = useState<number>(0);
  return (
    <div className="flex flex-col gap-2">
      {items.map((item, i) => {
        const isOpen = open === i;
        return (
          <div key={item.title} className="rounded-xl border border-white/10 bg-[#0B1220]/85">
            <button
              type="button"
              onClick={() => {
                soundFx.playMenuClick();
                setOpen(isOpen ? -1 : i);
              }}
              className="flex w-full items-center justify-between px-3 py-2.5 text-left text-xs font-semibold text-white cursor-pointer"
              aria-expanded={isOpen}
            >
              <span>{item.title}</span>
              <span className="text-slate-400">{isOpen ? '–' : '+'}</span>
            </button>
            {isOpen && (
              <ul className="flex flex-col gap-1.5 px-3 pb-3 text-[11px] leading-snug text-slate-300">
                {item.points.map((p) => (
                  <li key={p} className="flex gap-1.5">
                    <span className="mt-[5px] h-1 w-1 shrink-0 rounded-full bg-[#84CC16]" />
                    <span>{p}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Animated "Watch" tutorial: short scenes with SVG animation (works offline,
// no video file). Each scene lasts SCENE_SEC; timings below are fractions of it.
// ---------------------------------------------------------------------------

const SCENE_SEC = 6;
const DUR = SCENE_SEC + 's';
const BALL = '#FACC15';
const OK = '#84CC16';
const WARN = '#FB923C';
const BAD = '#F43F5E';

/** Visible from fraction a until fraction b (end of scene if b is 1). */
function vis(a: number, b = 1) {
  const a2 = Math.min(a + 0.01, 0.999);
  if (b >= 1) {
    return <animate attributeName="opacity" values="0;0;1;1" keyTimes={'0;' + a + ';' + a2 + ';1'} dur={DUR} fill="freeze" />;
  }
  const b2 = Math.min(b + 0.01, 0.999);
  return (
    <animate
      attributeName="opacity"
      values="0;0;1;1;0;0"
      keyTimes={'0;' + a + ';' + a2 + ';' + b + ';' + b2 + ';1'}
      dur={DUR}
      fill="freeze"
    />
  );
}

/** Move along a path between fractions a and b. */
function move(path: string, a: number, b: number) {
  return (
    <animateMotion
      dur={DUR}
      fill="freeze"
      calcMode="linear"
      keyPoints="0;0;1;1"
      keyTimes={'0;' + a + ';' + b + ';1'}
      path={path}
    />
  );
}

function Label({ x, y, text, color = '#F8FAFC', size = 7 }: { x: number; y: number; text: string; color?: string; size?: number }) {
  return (
    <text x={x} y={y} textAnchor="middle" fontSize={size} fontWeight="700" fill={color} stroke="#0B1220" strokeWidth="2" paintOrder="stroke">
      {text}
    </text>
  );
}

function CourtBase() {
  return (
    <g>
      <rect x="10" y="10" width="100" height="200" rx="2" fill="#1D4ED8" stroke="#F8FAFC" strokeWidth="1.5" />
      <rect x="10" y="75" width="100" height="70" fill="#1E40AF" stroke="#F8FAFC" strokeWidth="1" />
      <line x1="60" y1="10" x2="60" y2="75" stroke="#F8FAFC" strokeWidth="1" />
      <line x1="60" y1="145" x2="60" y2="210" stroke="#F8FAFC" strokeWidth="1" />
      <line x1="4" y1="110" x2="116" y2="110" stroke="#F8FAFC" strokeWidth="2.5" />
    </g>
  );
}

function Player({ x, y, color = '#F59E0B', name }: { x: number; y: number; color?: string; name?: string }) {
  return (
    <g>
      <circle cx={x} cy={y} r="5" fill={color} stroke="#0F172A" strokeWidth="1" />
      {name && <Label x={x} y={y + 12} text={name} size={6} />}
    </g>
  );
}

const SERVE_PATH = 'M86,200 Q55,95 36,44';

const TUTORIAL_SCENES: { title: string; caption: string; art: ReactNode }[] = [
  {
    title: '1. Serve diagonally',
    caption:
      'Serve underhand from behind the baseline into the box diagonally across. It must fly past the kitchen. Tap "Serve Diagonal" or press Space.',
    art: (
      <g>
        <rect x="10" y="10" width="50" height="65" fill={OK} opacity="0.15">
          <animate attributeName="opacity" values="0.08;0.32;0.08" dur="1.2s" repeatCount="indefinite" />
        </rect>
        <Player x={88} y={204} name="You" />
        <Player x={35} y={22} color="#A855F7" name="Lola" />
        <path d={SERVE_PATH} stroke="#FDE68A" strokeDasharray="2 2" fill="none" opacity="0.5" />
        <circle r="2.6" fill={BALL}>{move(SERVE_PATH, 0.15, 0.45)}</circle>
        <g opacity="0">
          {vis(0.46)}
          <circle cx="36" cy="44" r="6" fill="none" stroke={OK} strokeWidth="1.5" />
          <Label x={60} y={64} text="In the box!" color={OK} />
        </g>
      </g>
    ),
  },
  {
    title: '2. The two-bounce rule',
    caption:
      'The serve must bounce, and the return must bounce too. After those two bounces, anyone may volley (hit it in the air).',
    art: (
      <g>
        <Player x={88} y={204} name="You" />
        <Player x={35} y={22} color="#A855F7" name="Lola" />
        <circle r="2.6" fill={BALL} opacity="0">
          {vis(0.02, 0.3)}
          {move(SERVE_PATH, 0.05, 0.28)}
        </circle>
        <g opacity="0">
          {vis(0.28)}
          <circle cx="36" cy="44" r="5" fill="none" stroke={OK} strokeWidth="1.5" />
          <Label x={36} y={58} text="Bounce 1" color={OK} size={6} />
        </g>
        <circle r="2.6" fill={BALL} opacity="0">
          {vis(0.33, 0.62)}
          {move('M36,46 Q62,120 84,178', 0.34, 0.6)}
        </circle>
        <g opacity="0">
          {vis(0.6)}
          <circle cx="84" cy="178" r="5" fill="none" stroke={OK} strokeWidth="1.5" />
          <Label x={84} y={170} text="Bounce 2" color={OK} size={6} />
        </g>
        <circle r="2.6" fill={BALL} opacity="0">
          {vis(0.68)}
          {move('M84,184 Q70,100 70,52', 0.69, 0.9)}
        </circle>
        <g opacity="0">
          {vis(0.72)}
          <Label x={60} y={100} text="Now volleys are OK" color="#FDE68A" />
        </g>
      </g>
    ),
  },
  {
    title: '3. The kitchen',
    caption:
      'No volleys while standing in the kitchen (the zone next to the net). Stay behind the line to volley, or let the ball bounce first.',
    art: (
      <g>
        <rect x="10" y="110" width="100" height="35" fill={WARN} opacity="0.15">
          <animate attributeName="opacity" values="0.08;0.3;0.08" dur="1.4s" repeatCount="indefinite" />
        </rect>
        <Label x={60} y={140} text="KITCHEN" color="#FDE68A" size={6} />
        <Player x={35} y={124} name="In kitchen" />
        <Player x={85} y={160} name="Behind line" />
        <circle r="2.6" fill={BALL} opacity="0">
          {vis(0.05, 0.32)}
          {move('M38,30 Q42,80 36,119', 0.06, 0.3)}
        </circle>
        <g opacity="0">
          {vis(0.3)}
          <Label x={35} y={100} text="Fault!" color={BAD} size={8} />
        </g>
        <circle r="2.6" fill={BALL} opacity="0">
          {vis(0.45, 0.72)}
          {move('M82,30 Q86,100 85,155', 0.46, 0.7)}
        </circle>
        <g opacity="0">
          {vis(0.7)}
          <Label x={85} y={100} text="Volley OK" color={OK} size={8} />
        </g>
      </g>
    ),
  },
  {
    title: '4. Move and hit',
    caption:
      'Tap the court to run there. When the ball is inside your ring it turns green and you swing automatically. Orange means wait: "Let it bounce".',
    art: (
      <g>
        <g opacity="0">
          {vis(0.06, 0.22)}
          <circle cx="40" cy="168" r="3" fill="none" stroke="#F8FAFC" strokeWidth="1">
            <animate attributeName="r" values="2;10" dur="0.8s" repeatCount="indefinite" />
          </circle>
          <Label x={40} y={186} text="Tap here" size={6} />
        </g>
        <g>
          {move('M85,200 L40,168', 0.12, 0.34)}
          <circle cx="0" cy="0" r="13" fill="none" stroke="#94A3B8" strokeDasharray="2 2" strokeWidth="1" />
          <circle cx="0" cy="0" r="13" fill="none" stroke={WARN} strokeWidth="2" opacity="0">
            {vis(0.46, 0.6)}
          </circle>
          <circle cx="0" cy="0" r="13" fill={OK} fillOpacity="0.2" stroke={OK} strokeWidth="2" opacity="0">
            {vis(0.6, 0.85)}
          </circle>
          <circle cx="0" cy="0" r="5" fill="#F59E0B" stroke="#0F172A" strokeWidth="1" />
        </g>
        <circle r="2.6" fill={BALL} opacity="0">
          {vis(0.36, 0.6)}
          {move('M70,30 Q52,110 44,150', 0.37, 0.56)}
        </circle>
        <g opacity="0">
          {vis(0.47, 0.6)}
          <Label x={40} y={140} text="Let it bounce" color={WARN} size={6} />
        </g>
        <circle r="2.6" fill={BALL} opacity="0">
          {vis(0.6)}
          {move('M44,156 Q62,90 76,36', 0.64, 0.88)}
        </circle>
        <g opacity="0">
          {vis(0.6, 0.9)}
          <Label x={40} y={140} text="Swing!" color={OK} size={7} />
        </g>
      </g>
    ),
  },
  {
    title: '5. Scoring',
    caption:
      'Games go to 11 (or 15/21) and you must win by 2. Rally scoring: every rally scores a point. Side-out: only the serving side can score.',
    art: (
      <g>
        <rect x="16" y="70" width="88" height="80" rx="8" fill="#0B1220" stroke="#334155" />
        <Label x={60} y={86} text="You  –  Lola" color="#94A3B8" size={7} />
        <g opacity="0">{vis(0.01, 0.22)}<Label x={60} y={118} text="0 – 0" size={20} /></g>
        <g opacity="0">{vis(0.22, 0.46)}<Label x={60} y={118} text="6 – 4" size={20} /></g>
        <g opacity="0">
          {vis(0.46, 0.7)}
          <Label x={60} y={118} text="10 – 10" size={20} />
          <Label x={60} y={138} text="Need a 2-point lead" color={WARN} size={6} />
        </g>
        <g opacity="0">
          {vis(0.7)}
          <Label x={60} y={118} text="12 – 10" size={20} color={OK} />
          <Label x={60} y={138} text="Game! Won by 2" color={OK} size={7} />
        </g>
      </g>
    ),
  },
];

function TutorialPlayer() {
  const [scene, setScene] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [elapsed, setElapsed] = useState(0);
  const [loop, setLoop] = useState(0);
  const svgRef = useRef<SVGSVGElement | null>(null);
  const last = TUTORIAL_SCENES.length - 1;

  const goTo = (i: number) => {
    setScene(Math.max(0, Math.min(last, i)));
    setElapsed(0);
    setLoop((n) => n + 1);
    setPlaying(true);
  };

  useEffect(() => {
    if (!playing) return;
    const id = setInterval(() => setElapsed((e) => e + 0.1), 100);
    return () => clearInterval(id);
  }, [playing]);

  useEffect(() => {
    if (elapsed < SCENE_SEC) return;
    if (scene < last) goTo(scene + 1);
    else setPlaying(false);
  }, [elapsed]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) return;
    if (playing) svg.unpauseAnimations();
    else svg.pauseAnimations();
  }, [playing, scene, loop]);

  const togglePlay = () => {
    soundFx.playMenuClick();
    if (!playing && scene === last && elapsed >= SCENE_SEC) goTo(0);
    else setPlaying((p) => !p);
  };

  const current = TUTORIAL_SCENES[scene];
  const progress = Math.min(1, elapsed / SCENE_SEC);

  return (
    <div className="flex flex-col gap-2">
      <div className="overflow-hidden rounded-xl border border-white/10 bg-[#0B1220]">
        <div className="flex items-center justify-between px-3 pt-2 text-[11px] font-bold text-white">
          <span>{current.title}</span>
          <span className="font-mono text-slate-400">
            {scene + 1}/{TUTORIAL_SCENES.length}
          </span>
        </div>
        <svg
          key={scene + '-' + loop}
          ref={svgRef}
          viewBox="0 0 120 220"
          className="mx-auto block h-56 w-auto"
          role="img"
          aria-label={current.title}
        >
          <CourtBase />
          {current.art}
        </svg>
        <div className="h-1 w-full bg-slate-800">
          <div className="h-1 bg-[#84CC16] transition-[width] duration-100" style={{ width: progress * 100 + '%' }} />
        </div>
      </div>

      <p className="min-h-[48px] text-[11px] leading-snug text-slate-300">{current.caption}</p>

      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={() => {
            soundFx.playMenuClick();
            goTo(scene - 1);
          }}
          disabled={scene === 0}
          className="rounded-lg border border-white/15 px-3 py-1.5 text-xs font-semibold text-slate-200 disabled:opacity-40 cursor-pointer"
        >
          ◀ Prev
        </button>
        <button
          type="button"
          onClick={togglePlay}
          className="rounded-lg bg-[#84CC16] px-4 py-1.5 text-xs font-bold text-[#0B1220] cursor-pointer"
        >
          {playing ? 'Pause' : scene === last && elapsed >= SCENE_SEC ? 'Replay' : 'Play'}
        </button>
        <button
          type="button"
          onClick={() => {
            soundFx.playMenuClick();
            goTo(scene + 1);
          }}
          disabled={scene === last}
          className="rounded-lg border border-white/15 px-3 py-1.5 text-xs font-semibold text-slate-200 disabled:opacity-40 cursor-pointer"
        >
          Next ▶
        </button>
      </div>

      <div className="flex justify-center gap-1.5">
        {TUTORIAL_SCENES.map((s, i) => (
          <button
            key={s.title}
            type="button"
            aria-label={'Go to ' + s.title}
            onClick={() => goTo(i)}
            className={'h-2 w-2 rounded-full cursor-pointer ' + (i === scene ? 'bg-[#84CC16]' : 'bg-slate-600')}
          />
        ))}
      </div>
    </div>
  );
}

export function LearnView({ onBack }: { onBack: () => void }) {
  const [tab, setTab] = useState<LearnTab>('VIDEO');
  const tabs: { id: LearnTab; label: string }[] = [
    { id: 'VIDEO', label: 'Watch' },
    { id: 'RULES', label: 'Rules' },
    { id: 'CONTROLS', label: 'Controls' },
    { id: 'TIPS', label: 'Tips' },
  ];

  return (
    <div className="flex h-full w-full flex-col bg-[#15223B]">
      <div className="flex shrink-0 items-center gap-2 border-b border-white/10 bg-[#0B1220] px-3 py-2.5">
        <button
          type="button"
          onClick={() => {
            soundFx.playMenuClick();
            onBack();
          }}
          className="rounded-lg border border-white/15 px-2.5 py-1 text-xs font-semibold text-slate-200 hover:bg-[#1E293B] cursor-pointer"
        >
          ← Back
        </button>
        <h2 className="text-sm font-bold text-white">Learn Pickleball</h2>
      </div>

      <div className="shrink-0 px-3 pt-3">
        <div className="grid grid-cols-4 gap-1 rounded-xl border border-white/10 bg-[#0B1220]/85 p-1">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => {
                soundFx.playMenuClick();
                setTab(t.id);
              }}
              className={`rounded-lg py-1.5 text-[11px] font-semibold transition-colors cursor-pointer ${
                tab === t.id ? 'bg-[#1E293B] text-white' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-3 py-3">
        {tab === 'VIDEO' && <TutorialPlayer />}
        {tab === 'RULES' && (
          <>
            <CourtDiagram />
            <div className="mt-3">
              <ItemList items={RULES} />
            </div>
          </>
        )}
        {tab === 'CONTROLS' && <ItemList items={CONTROLS} />}
        {tab === 'TIPS' && <ItemList items={TIPS} />}
        <p className="mt-4 text-center text-[10px] text-slate-500">
          Rules summarized from the official USA Pickleball rulebook.
        </p>
      </div>
    </div>
  );
}
