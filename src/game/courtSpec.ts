export type CourtSide = 'LOWER' | 'UPPER';
export type ServiceBoxSide = 'LEFT' | 'RIGHT';

export type CourtZone =
  | 'LOWER_KITCHEN'
  | 'UPPER_KITCHEN'
  | 'LOWER_LEFT_SERVICE'
  | 'LOWER_RIGHT_SERVICE'
  | 'UPPER_LEFT_SERVICE'
  | 'UPPER_RIGHT_SERVICE'
  | 'NET_LINE'
  | 'OUT_OF_BOUNDS';

export const CourtColors = {
  SURROUND_NAVY: '#15223B',
  COURT_BLUE: '#1D56B8',
  KITCHEN_BLUE: '#164494',
  LINE_WHITE: '#F8FAFC',
  NET_MESH: '#CBD5E1',
  NET_TAPE: '#FFFFFF',
  NET_POST: '#0F172A',
  NET_SHADOW: 'rgba(13, 20, 41, 0.38)',
} as const;

export const CourtSpec = {
  WORLD_WIDTH_FT: 28,
  WORLD_HEIGHT_FT: 56,

  COURT_WIDTH_FT: 20,
  COURT_LENGTH_FT: 44,
  KITCHEN_DEPTH_FT: 7,
  SERVICE_DEPTH_FT: 15,
  LINE_WIDTH_FT: 2 / 12,

  NET_HEIGHT_SIDELINE_FT: 3.0,
  NET_HEIGHT_CENTER_FT: 34 / 12,
  NET_POST_OFFSET_FT: 1.0,

  COURT_LEFT_X: 4,
  COURT_RIGHT_X: 24,
  COURT_CENTER_X: 14,

  COURT_BOTTOM_Y: 6,
  COURT_TOP_Y: 50,
  NET_Y: 28,

  LOWER_NVZ_Y: 21,
  UPPER_NVZ_Y: 35,

  FIXED_TIMESTEP_SEC: 1 / 60,

  isInBounds(x: number, y: number): boolean {
    return (
      x >= this.COURT_LEFT_X &&
      x <= this.COURT_RIGHT_X &&
      y >= this.COURT_BOTTOM_Y &&
      y <= this.COURT_TOP_Y
    );
  },

  isInKitchen(x: number, y: number, side?: CourtSide): boolean {
    if (x < this.COURT_LEFT_X || x > this.COURT_RIGHT_X) return false;
    if (side === 'LOWER') return y >= this.LOWER_NVZ_Y && y <= this.NET_Y;
    if (side === 'UPPER') return y >= this.NET_Y && y <= this.UPPER_NVZ_Y;
    return y >= this.LOWER_NVZ_Y && y <= this.UPPER_NVZ_Y;
  },

  isInServiceBox(
    x: number,
    y: number,
    courtSide: CourtSide,
    boxSide: ServiceBoxSide,
  ): boolean {
    const xMatch =
      boxSide === 'LEFT'
        ? x >= this.COURT_LEFT_X && x <= this.COURT_CENTER_X
        : x >= this.COURT_CENTER_X && x <= this.COURT_RIGHT_X;
    if (!xMatch) return false;
    return courtSide === 'LOWER'
      ? y >= this.COURT_BOTTOM_Y && y <= this.LOWER_NVZ_Y
      : y >= this.UPPER_NVZ_Y && y <= this.COURT_TOP_Y;
  },

  classifyPoint(x: number, y: number): CourtZone {
    if (!this.isInBounds(x, y)) return 'OUT_OF_BOUNDS';
    if (Math.abs(y - this.NET_Y) <= this.LINE_WIDTH_FT * 0.5) return 'NET_LINE';

    if (y < this.LOWER_NVZ_Y) {
      return x <= this.COURT_CENTER_X ? 'LOWER_LEFT_SERVICE' : 'LOWER_RIGHT_SERVICE';
    }
    if (y < this.NET_Y) return 'LOWER_KITCHEN';
    if (y <= this.UPPER_NVZ_Y) return 'UPPER_KITCHEN';
    return x <= this.COURT_CENTER_X ? 'UPPER_LEFT_SERVICE' : 'UPPER_RIGHT_SERVICE';
  },

  netHeightAt(x: number): number {
    const normalized = Math.min(
      1,
      Math.max(0, Math.abs(x - this.COURT_CENTER_X) / (this.COURT_WIDTH_FT / 2)),
    );
    return (
      this.NET_HEIGHT_CENTER_FT +
      (this.NET_HEIGHT_SIDELINE_FT - this.NET_HEIGHT_CENTER_FT) * normalized
    );
  },
};

export const ZONE_LABELS: Record<CourtZone, string> = {
  LOWER_KITCHEN: 'Lower Kitchen (NVZ)',
  UPPER_KITCHEN: 'Upper Kitchen (NVZ)',
  LOWER_LEFT_SERVICE: 'Lower Left Service Box',
  LOWER_RIGHT_SERVICE: 'Lower Right Service Box',
  UPPER_LEFT_SERVICE: 'Upper Left Service Box',
  UPPER_RIGHT_SERVICE: 'Upper Right Service Box',
  NET_LINE: 'Center Net',
  OUT_OF_BOUNDS: 'Out of Bounds (Apron)',
};

export interface SpecTestResult {
  id: string;
  name: string;
  passed: boolean;
  detail: string;
}

export function runCourtSpecTests(): SpecTestResult[] {
  const results: SpecTestResult[] = [];

  const widthOk = CourtSpec.COURT_RIGHT_X - CourtSpec.COURT_LEFT_X === 20;
  const lengthOk = CourtSpec.COURT_TOP_Y - CourtSpec.COURT_BOTTOM_Y === 44;
  const kitchenOk =
    CourtSpec.NET_Y - CourtSpec.LOWER_NVZ_Y === 7 &&
    CourtSpec.UPPER_NVZ_Y - CourtSpec.NET_Y === 7;
  const serviceOk =
    CourtSpec.LOWER_NVZ_Y - CourtSpec.COURT_BOTTOM_Y === 15 &&
    CourtSpec.COURT_TOP_Y - CourtSpec.UPPER_NVZ_Y === 15;

  results.push({
    id: 'dimensions',
    name: 'Official USA Pickleball dimensions (20 × 44 ft)',
    passed: widthOk && lengthOk && kitchenOk && serviceOk,
    detail: '20 ft width · 44 ft length · 7 ft NVZ · 15 ft service depth',
  });

  const leftNet = Math.abs(CourtSpec.netHeightAt(CourtSpec.COURT_LEFT_X) - 3.0) < 0.0001;
  const rightNet = Math.abs(CourtSpec.netHeightAt(CourtSpec.COURT_RIGHT_X) - 3.0) < 0.0001;
  const centerNet =
    Math.abs(CourtSpec.netHeightAt(CourtSpec.COURT_CENTER_X) - 34 / 12) < 0.0001;

  results.push({
    id: 'net-sag',
    name: 'Net sag interpolation (36 in posts to 34 in center)',
    passed: leftNet && rightNet && centerNet,
    detail: 'Sideline 3.00 ft · Center strap 2.83 ft',
  });

  const classifyOk =
    CourtSpec.classifyPoint(1, 28) === 'OUT_OF_BOUNDS' &&
    CourtSpec.classifyPoint(14, 52) === 'OUT_OF_BOUNDS' &&
    CourtSpec.classifyPoint(14, CourtSpec.NET_Y) === 'NET_LINE' &&
    CourtSpec.classifyPoint(14, 24) === 'LOWER_KITCHEN' &&
    CourtSpec.classifyPoint(14, 32) === 'UPPER_KITCHEN' &&
    CourtSpec.classifyPoint(9, 12) === 'LOWER_LEFT_SERVICE' &&
    CourtSpec.classifyPoint(19, 12) === 'LOWER_RIGHT_SERVICE' &&
    CourtSpec.classifyPoint(9, 42) === 'UPPER_LEFT_SERVICE' &&
    CourtSpec.classifyPoint(19, 42) === 'UPPER_RIGHT_SERVICE';

  results.push({
    id: 'classify',
    name: 'Zone classifier covers kitchen, service boxes, net, and apron',
    passed: classifyOk,
    detail: '9 / 9 spatial zone assertions verified',
  });

  const boundaryOk =
    CourtSpec.isInBounds(CourtSpec.COURT_LEFT_X, CourtSpec.COURT_BOTTOM_Y) &&
    CourtSpec.isInBounds(CourtSpec.COURT_RIGHT_X, CourtSpec.COURT_TOP_Y) &&
    !CourtSpec.isInBounds(CourtSpec.COURT_LEFT_X - 0.1, CourtSpec.NET_Y) &&
    CourtSpec.isInKitchen(10, 21, 'LOWER') &&
    CourtSpec.isInKitchen(10, 35, 'UPPER') &&
    !CourtSpec.isInKitchen(10, 20.9, 'LOWER') &&
    CourtSpec.isInServiceBox(8, 15, 'LOWER', 'LEFT') &&
    !CourtSpec.isInServiceBox(8, 15, 'LOWER', 'RIGHT');

  results.push({
    id: 'boundaries',
    name: 'Line inclusivity & kitchen/service boundary checks',
    passed: boundaryOk,
    detail: 'Lines count in-bounds · NVZ line belongs to kitchen',
  });

  return results;
}
