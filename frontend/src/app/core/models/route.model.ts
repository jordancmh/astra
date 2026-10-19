export interface RouteCalculateRequest {
  routeId: string;
  vehicleId: string;
  originLat: number;
  originLng: number;
  destLat: number;
  destLng: number;
  avoidAnomalyIds?: string[];
}

export interface GeoJsonLineString {
  type: 'LineString';
  coordinates: [number, number][]; // [longitude, latitude]
}

export interface RouteCalculateResponse {
  routeId: string;
  status: string;
  distanceMeters: number;
  estimatedTimeMs: number;
  geometry: GeoJsonLineString;
}
