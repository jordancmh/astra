import {
  Component,
  AfterViewInit,
  OnDestroy,
  ElementRef,
  ViewChild,
  ChangeDetectionStrategy,
  inject,
  effect,
} from '@angular/core';
import mapboxgl from 'mapbox-gl';
import { environment } from '../../../environments/environment';
import { RoutingService } from '../../core/services/routing.service';
import { AnomalyService } from '../../core/services/anomaly.service';
import { GeoJsonLineString, RouteCalculateRequest } from '../../core/models/route.model';
import { NetworkAnomaly } from '../../core/models/anomaly.model';

/** Klang Valley bounding box: SW [lng, lat] -> NE [lng, lat] (WGS 84) */
const KLANG_VALLEY_BOUNDS: mapboxgl.LngLatBoundsLike = [
  [101.40, 2.90],
  [101.90, 3.30],
];

/** Approximate centre of Klang Valley (Kuala Lumpur) */
const KL_CENTER: [number, number] = [101.6839, 3.1390];

/** Prototype origin (KL city center) and destination (Shah Alam / Subang) */
const PROTOTYPE_ORIGIN: [number, number] = [101.6839, 3.1390];
const PROTOTYPE_DESTINATION: [number, number] = [101.5540, 3.0755];

@Component({
  selector: 'app-map-view',
  standalone: true,
  templateUrl: './map-view.component.html',
  styleUrl: './map-view.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MapViewComponent implements AfterViewInit, OnDestroy {
  @ViewChild('mapContainer', { static: true })
  private mapContainerRef!: ElementRef<HTMLDivElement>;

  private readonly routingService = inject(RoutingService);
  readonly anomalyService = inject(AnomalyService);

  private map!: mapboxgl.Map;
  private mapLoaded = false;
  private currentPopup: mapboxgl.Popup | null = null;

  constructor() {
    // Reactively update Mapbox anomaly GeoJSON layers when active anomalies change
    effect(() => {
      const anomalies = this.anomalyService.activeAnomalies();
      if (this.mapLoaded) {
        this.updateAnomalyFeatures(anomalies);
      }
    });

    // Reactively update map canvas cursor based on click-picker state
    effect(() => {
      const isDropping = this.anomalyService.isDroppingOnMap();
      if (this.mapLoaded && this.map) {
        const canvas = this.map.getCanvas?.();
        if (canvas) {
          canvas.style.cursor = isDropping ? 'crosshair' : '';
        }
      }
    });
  }

  ngAfterViewInit(): void {
    mapboxgl.accessToken = environment.mapboxToken;

    this.map = new mapboxgl.Map({
      container: this.mapContainerRef.nativeElement,
      style: 'mapbox://styles/mapbox/dark-v11',
      center: KL_CENTER,
      zoom: 11,
      maxBounds: KLANG_VALLEY_BOUNDS,
      attributionControl: false,
    });

    this.map.addControl(
      new mapboxgl.NavigationControl({ visualizePitch: true }),
      'top-right'
    );

    this.map.addControl(
      new mapboxgl.AttributionControl({ compact: true }),
      'bottom-right'
    );

    this.map.addControl(
      new mapboxgl.ScaleControl({ maxWidth: 120, unit: 'metric' }),
      'bottom-left'
    );

    this.map.on('load', () => {
      console.log('[ASTRA] Mapbox GL JS loaded — Klang Valley spatial viewport ready.');
      this.mapLoaded = true;
      this.setupAnomalyLayers();
      this.setupMapClickHandlers();
      this.loadPrototypeRoute();
      this.anomalyService.fetchActiveAnomalies().subscribe();
    });
  }

  private setupAnomalyLayers(): void {
    const sourceId = 'anomalies-source';
    if (!this.map.getSource(sourceId)) {
      this.map.addSource(sourceId, {
        type: 'geojson',
        data: {
          type: 'FeatureCollection',
          features: [],
        },
      });
    }

    if (!this.map.getLayer('anomalies-fill')) {
      this.map.addLayer({
        id: 'anomalies-fill',
        type: 'fill',
        source: sourceId,
        paint: {
          'fill-color': [
            'match',
            ['get', 'anomalyType'],
            'ROAD_CLOSURE', '#ef4444',
            'CONGESTION', '#f59e0b',
            'WEATHER', '#06b6d4',
            'ACCIDENT', '#f97316',
            '#ef4444',
          ],
          'fill-opacity': 0.35,
        },
      });
    }

    if (!this.map.getLayer('anomalies-line')) {
      this.map.addLayer({
        id: 'anomalies-line',
        type: 'line',
        source: sourceId,
        paint: {
          'line-color': [
            'match',
            ['get', 'anomalyType'],
            'ROAD_CLOSURE', '#ef4444',
            'CONGESTION', '#f59e0b',
            'WEATHER', '#06b6d4',
            'ACCIDENT', '#f97316',
            '#ef4444',
          ],
          'line-width': 2,
          'line-opacity': 0.9,
        },
      });
    }

    // Immediately synchronize any existing anomalies
    this.updateAnomalyFeatures(this.anomalyService.activeAnomalies());
  }

  private setupMapClickHandlers(): void {
    // Map Canvas Click — Coordinate Dropper
    this.map.on('click', (e: any) => {
      if (this.anomalyService.isDroppingOnMap()) {
        const lng = e.lngLat.lng;
        const lat = e.lngLat.lat;
        if (this.anomalyService.isWithinKlangValley(lng, lat)) {
          this.anomalyService.setSelectedCoordinates([lng, lat]);
          this.anomalyService.setDroppingOnMap(false);
        } else {
          console.warn('[ASTRA] Clicked coordinate outside Klang Valley bounds:', [lng, lat]);
        }
      }
    });

    // Anomaly Fill Click — Inspector Popup
    this.map.on('click', 'anomalies-fill', (e: any) => {
      if (this.anomalyService.isDroppingOnMap()) return;
      if (!e.features || e.features.length === 0) return;

      const props = e.features[0].properties;
      if (!props) return;

      const popupHtml = document.createElement('div');
      popupHtml.className = 'p-2 text-slate-900 text-xs font-sans min-w-[180px]';
      popupHtml.innerHTML = `
        <div class="font-bold text-sm mb-1">${props.anomalyType}</div>
        <div class="text-xs text-gray-700 mb-0.5">ID: <b>${props.id}</b></div>
        <div class="text-xs text-gray-700 mb-0.5">Severity: <b>${props.severity}</b></div>
        <div class="text-xs text-gray-700 mb-2">Radius: <b>${props.radiusMeters}m</b></div>
        <button id="btn-clear-${props.id}" class="w-full bg-rose-600 hover:bg-rose-700 text-white font-medium py-1 px-2 rounded text-xs cursor-pointer transition-colors">
          Clear Disruption
        </button>
      `;

      popupHtml.querySelector(`#btn-clear-${props.id}`)?.addEventListener('click', () => {
        this.anomalyService.clearAnomaly(props.id).subscribe();
        this.currentPopup?.remove();
      });

      this.currentPopup = new mapboxgl.Popup({ closeButton: true, closeOnClick: true })
        .setLngLat(e.lngLat)
        .setDOMContent(popupHtml)
        .addTo(this.map);
    });

    this.map.on('mouseenter', 'anomalies-fill', () => {
      if (!this.anomalyService.isDroppingOnMap()) {
        const canvas = this.map.getCanvas?.();
        if (canvas) canvas.style.cursor = 'pointer';
      }
    });

    this.map.on('mouseleave', 'anomalies-fill', () => {
      if (!this.anomalyService.isDroppingOnMap()) {
        const canvas = this.map.getCanvas?.();
        if (canvas) canvas.style.cursor = '';
      }
    });
  }

  private updateAnomalyFeatures(anomalies: NetworkAnomaly[]): void {
    if (!this.map) return;

    const source = this.map.getSource('anomalies-source') as mapboxgl.GeoJSONSource | undefined;
    if (!source) return;

    const features: GeoJSON.Feature[] = anomalies
      .filter((a) => a.polygon && a.polygon.coordinates)
      .map((a) => ({
        type: 'Feature',
        id: a.id,
        properties: {
          id: a.id,
          anomalyType: a.anomalyType,
          severity: a.severity,
          radiusMeters: a.radiusMeters,
          injectedAt: a.injectedAt,
        },
        geometry: {
          type: 'Polygon',
          coordinates: a.polygon.coordinates,
        },
      }));

    source.setData({
      type: 'FeatureCollection',
      features,
    });
  }

  private loadPrototypeRoute(): void {
    const request: RouteCalculateRequest = {
      routeId: 'RT-PROTOTYPE-01',
      vehicleId: 'V-001',
      originLat: PROTOTYPE_ORIGIN[1],
      originLng: PROTOTYPE_ORIGIN[0],
      destLat: PROTOTYPE_DESTINATION[1],
      destLng: PROTOTYPE_DESTINATION[0],
      avoidAnomalyIds: [],
    };

    this.routingService.calculateRoute(request).subscribe({
      next: (response) => {
        this.renderRouteGeometry(response.geometry);
      },
      error: (err) => {
        console.warn('[ASTRA] Failed to load prototype route from backend:', err);
      },
    });
  }

  private renderRouteGeometry(geometry: GeoJsonLineString): void {
    if (!this.map) return;

    const sourceId = 'active-route';
    const layerId = 'active-route-line';

    const existingSource = this.map.getSource(sourceId) as mapboxgl.GeoJSONSource | undefined;
    if (existingSource) {
      existingSource.setData(geometry as any);
    } else {
      this.map.addSource(sourceId, {
        type: 'geojson',
        data: geometry as any,
      });
    }

    if (!this.map.getLayer(layerId)) {
      this.map.addLayer({
        id: layerId,
        type: 'line',
        source: sourceId,
        layout: {
          'line-join': 'round',
          'line-cap': 'round',
        },
        paint: {
          'line-color': '#06b6d4', // --color-accent (cyan)
          'line-width': 4,
          'line-opacity': 0.9,
        },
      });
    }

    if (geometry.coordinates && geometry.coordinates.length > 0) {
      const bounds = geometry.coordinates.reduce(
        (b, coord) => b.extend(coord as [number, number]),
        new mapboxgl.LngLatBounds(geometry.coordinates[0], geometry.coordinates[0])
      );
      this.map.fitBounds(bounds, { padding: 40, maxZoom: 14 });
    }
  }

  ngOnDestroy(): void {
    this.currentPopup?.remove();
    this.map?.remove();
  }
}
