export type AnomalyType = 'ROAD_CLOSURE' | 'CONGESTION' | 'WEATHER' | 'ACCIDENT';

export type AnomalySeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface GeoJsonPolygon {
  type: 'Polygon';
  coordinates: [number, number][][]; // GeoJSON polygon rings: [[[lng, lat], ...]]
}

export interface NetworkAnomaly {
  id: string;
  anomalyType: AnomalyType;
  severity: AnomalySeverity;
  radiusMeters: number;
  center: [number, number]; // [longitude, latitude]
  polygon: GeoJsonPolygon;
  injectedAt: string;
  isActive?: boolean;
}

export interface AnomalyInjectRequest {
  anomalyType: AnomalyType;
  severity: AnomalySeverity;
  latitude: number;
  longitude: number;
  radiusMeters: number;
}

export interface AnomalyInjectResponse {
  anomalyId: string;
  anomalyType: AnomalyType;
  severity: AnomalySeverity;
  affectedRouteIds: string[];
  affectedVehicleIds: string[];
  injectedAt: string;
}

export interface AnomalyClearResponse {
  clearedCount: number;
  timestamp: string;
}

export interface AnomalyPreset {
  id: string;
  name: string;
  corridor: string;
  type: AnomalyType;
  severity: AnomalySeverity;
  latitude: number;
  longitude: number;
  radiusMeters: number;
  description: string;
}

export interface AnomalyEvent {
  id: string;
  eventType: 'INJECTED' | 'CLEARED' | 'ALL_CLEARED';
  anomalyId?: string;
  anomalyType?: AnomalyType;
  message: string;
  severity: AnomalySeverity;
  timestamp: Date;
}

export const KLANG_VALLEY_BOUNDS = {
  minLat: 2.90,
  maxLat: 3.30,
  minLng: 101.40,
  maxLng: 101.90,
} as const;

export const ANOMALY_TYPE_CONFIG: Record<
  AnomalyType,
  { label: string; color: string; fillOpacity: number; strokeWidth: number }
> = {
  ROAD_CLOSURE: { label: 'Road Closure', color: '#ef4444', fillOpacity: 0.35, strokeWidth: 2 },
  CONGESTION: { label: 'Congestion', color: '#f59e0b', fillOpacity: 0.30, strokeWidth: 2 },
  WEATHER: { label: 'Severe Weather', color: '#06b6d4', fillOpacity: 0.25, strokeWidth: 1.5 },
  ACCIDENT: { label: 'Major Accident', color: '#f97316', fillOpacity: 0.35, strokeWidth: 2 },
};

export const CORRIDOR_PRESETS: AnomalyPreset[] = [
  {
    id: 'preset-fed-hwy',
    name: 'Federal Highway',
    corridor: 'Subang Jaya — Petaling Jaya',
    type: 'ROAD_CLOSURE',
    severity: 'HIGH',
    latitude: 3.0833,
    longitude: 101.5833,
    radiusMeters: 600,
    description: 'Complete arterial closure along KM 14 Federal Highway bottleneck',
  },
  {
    id: 'preset-ldp',
    name: 'LDP (Kelana Jaya)',
    corridor: 'Kelana Jaya — Puchong',
    type: 'CONGESTION',
    severity: 'MEDIUM',
    latitude: 3.1085,
    longitude: 101.6035,
    radiusMeters: 500,
    description: 'Heavy bumper-to-bumper queue approaching Kelana Jaya flyover',
  },
  {
    id: 'preset-npe',
    name: 'NPE (Bangsar / Pantai)',
    corridor: 'Pantai Dalam — Bangsar',
    type: 'ACCIDENT',
    severity: 'CRITICAL',
    latitude: 3.1180,
    longitude: 101.6620,
    radiusMeters: 400,
    description: 'Multi-vehicle collision blocking two central lanes',
  },
  {
    id: 'preset-klcc',
    name: 'KLCC Core',
    corridor: 'Jalan Ampang / City Centre',
    type: 'WEATHER',
    severity: 'LOW',
    latitude: 3.1579,
    longitude: 101.7116,
    radiusMeters: 800,
    description: 'Tropical downpour causing flash localized ponding',
  },
];
