import { CHARACTER_PALETTES, type CharacterId } from '../game/ballPhysics';

interface CharacterPortraitProps {
  characterId: CharacterId;
  size?: number;
  locked?: boolean;
}

/**
 * 64x64 pixel-inspired chibi front-view SVG portrait matching our sprite sheet design spec.
 * Designed so real 64x64 TextureAtlas menu sprites drop into this slot later without layout shifts.
 */
export function CharacterPortrait({
  characterId,
  size = 56,
  locked = false,
}: CharacterPortraitProps) {
  const pal = CHARACTER_PALETTES[characterId];
  const shirt = locked ? '#334155' : pal.shirt;
  const skin = locked ? '#475569' : pal.skin;
  const headwear = locked ? '#64748B' : pal.headwear;
  const paddle = locked ? '#475569' : pal.paddle;
  const hair = locked ? '#334155' : pal.hair;
  const bodyScale = pal.bigBuild ? 1.12 : 1;

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      className="shrink-0"
      aria-label={`${pal.name} portrait`}
    >
      {/* Court swatch frame */}
      <rect x="2" y="2" width="60" height="60" rx="12" fill="#0B1220" stroke="#1E293B" strokeWidth="2" />

      {/* Paddle on right */}
      <rect x="49" y="34" width="4" height="11" rx="1.5" fill="#78350F" stroke="#0F172A" strokeWidth="1.5" />
      <rect x="44" y="18" width="13" height="18" rx="4" fill={paddle} stroke="#0F172A" strokeWidth="2" />
      {!locked && <rect x="47" y="21" width="3" height="7" rx="1" fill="rgba(255,255,255,0.28)" />}

      {/* Shirt / Torso */}
      <g transform={`translate(32 0) scale(${bodyScale} 1) translate(-32 0)`}>
        <rect x="17" y="37" width="28" height="16" rx="4" fill={shirt} stroke="#0F172A" strokeWidth="2" />
        {/* Tank straps for Bert or collar accent */}
        {pal.tankTop ? (
          <>
            <rect x="20" y="37" width="5" height="6" fill={shirt} />
            <rect x="37" y="37" width="5" height="6" fill={shirt} />
            <rect x="25" y="37" width="12" height="4" fill={skin} />
          </>
        ) : (
          <rect x="26" y="42" width="10" height="3" rx="1" fill="rgba(255,255,255,0.75)" />
        )}
      </g>

      {/* Head */}
      <rect
        x={pal.isPickleMascot ? 19 : 17}
        y={pal.isPickleMascot ? 10 : 13}
        width={pal.isPickleMascot ? 24 : 28}
        height={pal.isPickleMascot ? 26 : 23}
        rx={pal.isPickleMascot ? 9 : 6}
        fill={skin}
        stroke="#0F172A"
        strokeWidth="2"
      />

      {/* Pickle bumps for Dill */}
      {pal.isPickleMascot && !locked && (
        <>
          <circle cx="23" cy="28" r="1.7" fill="#3F6212" opacity="0.55" />
          <circle cx="38" cy="29" r="1.9" fill="#3F6212" opacity="0.55" />
          <circle cx="35" cy="14" r="1.4" fill="#3F6212" opacity="0.55" />
        </>
      )}

      {/* Hair / Bun */}
      {!pal.isPickleMascot && (
        <path
          d="M17 19 C17 11, 45 11, 45 19 L45 21 L17 21 Z"
          fill={hair}
          stroke="#0F172A"
          strokeWidth="1.5"
        />
      )}

      {/* Headband / Visor */}
      <rect x="16" y="16" width="30" height="5" rx="2" fill={headwear} stroke="#0F172A" strokeWidth="1.5" />
      {characterId === 'LOLA' && (
        <rect x="21" y="20" width="20" height="3" rx="1" fill="#F472B6" stroke="#0F172A" strokeWidth="1" />
      )}
      {(characterId === 'RIO' || characterId === 'DILL') && (
        <rect x="29" y="17" width="4" height="3" rx="0.5" fill="rgba(255,255,255,0.85)" />
      )}

      {/* Eyes */}
      <rect x="24" y="24" width="3" height="4.5" rx="1" fill="#0F172A" />
      <rect x="35" y="24" width="3" height="4.5" rx="1" fill="#0F172A" />

      {/* Lola's glasses */}
      {pal.hasGlasses && (
        <g stroke="#0F172A" strokeWidth="1.6" fill="rgba(56,189,248,0.18)">
          <rect x="21.5" y="22.5" width="7.5" height="6.5" rx="1.5" />
          <rect x="33" y="22.5" width="7.5" height="6.5" rx="1.5" />
          <line x1="29" y1="25.5" x2="33" y2="25.5" />
        </g>
      )}

      {/* Bert's beard */}
      {pal.hasBeard && (
        <path
          d="M19 29 L43 29 L41 37 Q31 41 21 37 Z"
          fill={hair}
          stroke="#0F172A"
          strokeWidth="1.4"
        />
      )}

      {/* Smile */}
      {!pal.hasBeard && (
        <rect x="28" y="31" width="6" height="2" rx="1" fill="#9A3412" />
      )}
    </svg>
  );
}
