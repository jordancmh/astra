import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, throwError } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  AnomalyClearResponse,
  AnomalyEvent,
  AnomalyInjectRequest,
  AnomalyInjectResponse,
  GeoJsonPolygon,
  KLANG_VALLEY_BOUNDS,
  NetworkAnomaly,
} from '../models/anomaly.model';

@Injectable({
  providedIn: 'root',
})
export class AnomalyService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  readonly activeAnomalies = signal<NetworkAnomaly[]>([]);
  readonly isDroppingOnMap = signal<boolean>(false);
  readonly selectedCoordinates = signal<[number, number] | null>(null);
  readonly isLoading = signal<boolean>(false);
  readonly lastInjected = signal<AnomalyInjectResponse | null>(null);

  readonly anomalyCount = computed(() => this.activeAnomalies().length);
  readonly hasActiveAnomalies = computed(() => this.anomalyCount() > 0);
  readonly activeRoadClosures = computed(() =>
    this.activeAnomalies().filter((a) => a.anomalyType === 'ROAD_CLOSURE').length
  );

  readonly anomalyEvents$ = new Subject<AnomalyEvent>();

  isWithinKlangValley(longitude: number, latitude: number): boolean {
    return (
      longitude >= KLANG_VALLEY_BOUNDS.minLng &&
      longitude <= KLANG_VALLEY_BOUNDS.maxLng &&
      latitude >= KLANG_VALLEY_BOUNDS.minLat &&
      latitude <= KLANG_VALLEY_BOUNDS.maxLat
    );
  }

  fetchActiveAnomalies(): Observable<NetworkAnomaly[]> {
    this.isLoading.set(true);
    return this.http.get<NetworkAnomaly[]>(`${this.baseUrl}/v1/anomalies`).pipe(
      tap((anomalies) => {
        this.activeAnomalies.set(anomalies);
        this.isLoading.set(false);
      }),
      catchError((err) => {
        this.isLoading.set(false);
        return throwError(() => err);
      })
    );
  }

  injectAnomaly(request: AnomalyInjectRequest): Observable<AnomalyInjectResponse> {
    if (!this.isWithinKlangValley(request.longitude, request.latitude)) {
      return throwError(
        () =>
          new Error(
            `Coordinates [${request.longitude}, ${request.latitude}] are outside Klang Valley bounds (Lat: ${KLANG_VALLEY_BOUNDS.minLat}..${KLANG_VALLEY_BOUNDS.maxLat}, Lng: ${KLANG_VALLEY_BOUNDS.minLng}..${KLANG_VALLEY_BOUNDS.maxLng})`
          )
      );
    }

    this.isLoading.set(true);
    return this.http
      .post<AnomalyInjectResponse>(`${this.baseUrl}/v1/anomalies`, request)
      .pipe(
        tap((res) => {
          this.lastInjected.set(res);
          this.isLoading.set(false);
          this.setDroppingOnMap(false);
          this.setSelectedCoordinates(null);

          this.anomalyEvents$.next({
            id: `evt-${Date.now()}`,
            eventType: 'INJECTED',
            anomalyId: res.anomalyId,
            anomalyType: res.anomalyType,
            severity: res.severity,
            message: `${res.anomalyType} injected (${res.anomalyId}) affecting ${res.affectedRouteIds.length} routes`,
            timestamp: new Date(),
          });

          // Automatically synchronize active anomalies list
          this.fetchActiveAnomalies().subscribe();
        }),
        catchError((err) => {
          this.isLoading.set(false);
          return throwError(() => err);
        })
      );
  }

  clearAnomaly(id: string): Observable<void> {
    this.isLoading.set(true);
    return this.http.delete<void>(`${this.baseUrl}/v1/anomalies/${id}`).pipe(
      tap(() => {
        const remaining = this.activeAnomalies().filter((a) => a.id !== id);
        this.activeAnomalies.set(remaining);
        this.isLoading.set(false);

        this.anomalyEvents$.next({
          id: `evt-${Date.now()}`,
          eventType: 'CLEARED',
          anomalyId: id,
          severity: 'LOW',
          message: `Disruption ${id} cleared from network`,
          timestamp: new Date(),
        });
      }),
      catchError((err) => {
        this.isLoading.set(false);
        return throwError(() => err);
      })
    );
  }

  clearAllAnomalies(): Observable<AnomalyClearResponse> {
    this.isLoading.set(true);
    return this.http
      .delete<AnomalyClearResponse>(`${this.baseUrl}/v1/anomalies`)
      .pipe(
        tap((res) => {
          this.activeAnomalies.set([]);
          this.isLoading.set(false);

          this.anomalyEvents$.next({
            id: `evt-${Date.now()}`,
            eventType: 'ALL_CLEARED',
            severity: 'LOW',
            message: `All network disruptions cleared (${res.clearedCount} resolved)`,
            timestamp: new Date(),
          });
        }),
        catchError((err) => {
          this.isLoading.set(false);
          return throwError(() => err);
        })
      );
  }

  setDroppingOnMap(active: boolean): void {
    this.isDroppingOnMap.set(active);
  }

  setSelectedCoordinates(coords: [number, number] | null): void {
    this.selectedCoordinates.set(coords);
  }

  generateGeodesicPolygon(
    centerLng: number,
    centerLat: number,
    radiusMeters: number
  ): GeoJsonPolygon {
    const latDegreeMeters = 110574.0;
    const lngDegreeMeters = 111157.0;

    const dLat = radiusMeters / latDegreeMeters;
    const dLng = radiusMeters / lngDegreeMeters;

    const points = 16;
    const ring: [number, number][] = [];

    for (let i = 0; i < points; i++) {
      const angle = (2.0 * Math.PI * i) / points;
      let lng = centerLng + dLng * Math.cos(angle);
      let lat = centerLat + dLat * Math.sin(angle);

      // Clamp strictly within Klang Valley bounds
      lng = Math.max(KLANG_VALLEY_BOUNDS.minLng, Math.min(KLANG_VALLEY_BOUNDS.maxLng, lng));
      lat = Math.max(KLANG_VALLEY_BOUNDS.minLat, Math.min(KLANG_VALLEY_BOUNDS.maxLat, lat));

      ring.push([lng, lat]);
    }
    // Close ring
    ring.push([ring[0][0], ring[0][1]]);

    return {
      type: 'Polygon',
      coordinates: [ring],
    };
  }
}
