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
import { AnomalyService } from '../../core/services/anomaly.service';
import { FleetTelemetryService } from '../../core/services/fleet-telemetry.service';
import { NetworkAnomaly } from '../../core/models/anomaly.model';
import { FleetRoute, FleetVehicle } from '../../core/models/fleet.model';

/** Klang Valley bounding box: SW [lng, lat] -> NE [lng, lat] (WGS 84) */
const KLANG_VALLEY_BOUNDS: mapboxgl.LngLatBoundsLike = [
  [101.40, 2.90],
  [101.90, 3.30],
];

/** Approximate centre of Klang Valley (Kuala Lumpur) */
const KL_CENTER: [number, number] = [101.6839, 3.1390];

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

  readonly anomalyService = inject(AnomalyService);
  readonly fleetService = inject(FleetTelemetryService);

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

    // Reactively update active routes from FleetTelemetryService
    effect(() => {
      const routes = this.fleetService.routes();
      if (this.mapLoaded) {
        this.updateFleetRoutes(routes);
      }
    });

    // Reactively update vehicle positions & orientations from FleetTelemetryService
    effect(() => {
      const vehicles = this.fleetService.vehicles();
      if (this.mapLoaded) {
        this.updateFleetVehicles(vehicles);
      }
    });

    // Reactively fly camera to focused vehicle on selection
    effect(() => {
      const selectedId = this.fleetService.selectedVehicleId();
      if (this.mapLoaded && selectedId && this.map) {
        const vehicle = this.fleetService.vehicles().find((v) => v.id === selectedId);
        if (vehicle && typeof this.map.flyTo === 'function') {
          this.map.flyTo({
            center: [vehicle.longitude, vehicle.latitude],
            zoom: 13.5,
            speed: 1.2,
            curve: 1.4,
            essential: true,
          });
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
      this.loadDirectionArrowIcon();
      this.setupAnomalyLayers();
      this.setupFleetRouteLayers();
      this.setupFleetVehicleLayers();
      this.setupMapClickHandlers();

      this.anomalyService.fetchActiveAnomalies().subscribe();
      this.fleetService.fetchFleetState().subscribe();
    });
  }

  private loadDirectionArrowIcon(): void {
    if (!this.map || this.map.hasImage?.('vehicle-arrow-icon')) return;

    try {
      const size = 32;
      const canvas = document.createElement('canvas');
      canvas.width = size;
      canvas.height = size;
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.moveTo(16, 4);
        ctx.lineTo(26, 26);
        ctx.lineTo(16, 20);
        ctx.lineTo(6, 26);
        ctx.closePath();
        ctx.fill();
        const imgData = ctx.getImageData(0, 0, size, size);
        this.map.addImage('vehicle-arrow-icon', imgData);
      }
    } catch (e) {
      console.warn('[ASTRA] Could not create in-memory vehicle arrow icon:', e);
    }
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

    this.updateAnomalyFeatures(this.anomalyService.activeAnomalies());
  }

  private setupFleetRouteLayers(): void {
    const sourceId = 'fleet-routes-source';
    if (!this.map.getSource(sourceId)) {
      this.map.addSource(sourceId, {
        type: 'geojson',
        data: {
          type: 'FeatureCollection',
          features: [],
        },
      });
    }

    if (!this.map.getLayer('fleet-routes-line')) {
      this.map.addLayer({
        id: 'fleet-routes-line',
        type: 'line',
        source: sourceId,
        layout: {
          'line-join': 'round',
          'line-cap': 'round',
        },
        paint: {
          'line-color': [
            'match',
            ['get', 'status'],
            'NOMINAL', '#10b981',
            'OPTIMAL', '#10b981',
            'CONFLICT', '#f59e0b',
            'DISRUPTED', '#ef4444',
            'REROUTING', '#06b6d4',
            '#10b981',
          ],
          'line-width': 3.5,
          'line-opacity': 0.85,
        },
      });
    }

    this.updateFleetRoutes(this.fleetService.routes());
  }

  private setupFleetVehicleLayers(): void {
    const sourceId = 'fleet-vehicles-source';
    if (!this.map.getSource(sourceId)) {
      this.map.addSource(sourceId, {
        type: 'geojson',
        data: {
          type: 'FeatureCollection',
          features: [],
        },
      });
    }

    // Vehicle status pulse halo
    if (!this.map.getLayer('fleet-vehicles-pulse')) {
      this.map.addLayer({
        id: 'fleet-vehicles-pulse',
        type: 'circle',
        source: sourceId,
        paint: {
          'circle-radius': 14,
          'circle-color': [
            'match',
            ['get', 'status'],
            'NOMINAL', '#10b981',
            'INTERSECTED', '#f59e0b',
            'DISRUPTED', '#ef4444',
            'REROUTING', '#06b6d4',
            '#10b981',
          ],
          'circle-opacity': 0.25,
        },
      });
    }

    // Vehicle point
    if (!this.map.getLayer('fleet-vehicles-circle')) {
      this.map.addLayer({
        id: 'fleet-vehicles-circle',
        type: 'circle',
        source: sourceId,
        paint: {
          'circle-radius': 8,
          'circle-color': [
            'match',
            ['get', 'status'],
            'NOMINAL', '#10b981',
            'INTERSECTED', '#f59e0b',
            'DISRUPTED', '#ef4444',
            'REROUTING', '#06b6d4',
            '#10b981',
          ],
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff',
        },
      });
    }

    // Vehicle label layer
    if (!this.map.getLayer('fleet-vehicles-label')) {
      this.map.addLayer({
        id: 'fleet-vehicles-label',
        type: 'symbol',
        source: sourceId,
        layout: {
          'text-field': ['get', 'id'],
          'text-size': 11,
          'text-offset': [0, 1.4],
          'text-anchor': 'top',
          'text-allow-overlap': true,
        },
        paint: {
          'text-color': '#f8fafc',
          'text-halo-color': '#0f172a',
          'text-halo-width': 1.5,
        },
      });
    }

    this.updateFleetVehicles(this.fleetService.vehicles());
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

    // Vehicle click listener -> Focus vehicle
    this.map.on('click', 'fleet-vehicles-circle', (e: any) => {
      if (this.anomalyService.isDroppingOnMap()) return;
      if (!e.features || e.features.length === 0) return;
      const vId = e.features[0].properties?.id;
      if (vId) {
        this.fleetService.selectVehicle(vId);
      }
    });

    this.map.on('mouseenter', 'fleet-vehicles-circle', () => {
      if (!this.anomalyService.isDroppingOnMap()) {
        const canvas = this.map.getCanvas?.();
        if (canvas) canvas.style.cursor = 'pointer';
      }
    });

    this.map.on('mouseleave', 'fleet-vehicles-circle', () => {
      if (!this.anomalyService.isDroppingOnMap()) {
        const canvas = this.map.getCanvas?.();
        if (canvas) canvas.style.cursor = '';
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

  private updateFleetRoutes(routes: FleetRoute[]): void {
    if (!this.map) return;

    const source = this.map.getSource('fleet-routes-source') as mapboxgl.GeoJSONSource | undefined;
    if (!source) return;

    const features: GeoJSON.Feature[] = routes
      .filter((r) => r.geometry && r.geometry.coordinates && r.geometry.coordinates.length > 0)
      .map((r) => ({
        type: 'Feature',
        id: r.id,
        properties: {
          id: r.id,
          vehicleId: r.vehicleId,
          status: r.status,
          distanceMeters: r.distanceMeters,
        },
        geometry: {
          type: 'LineString',
          coordinates: r.geometry.coordinates,
        },
      }));

    source.setData({
      type: 'FeatureCollection',
      features,
    });
  }

  private updateFleetVehicles(vehicles: FleetVehicle[]): void {
    if (!this.map) return;

    const source = this.map.getSource('fleet-vehicles-source') as mapboxgl.GeoJSONSource | undefined;
    if (!source) return;

    const features: GeoJSON.Feature[] = vehicles.map((v) => ({
      type: 'Feature',
      id: v.id,
      properties: {
        id: v.id,
        routeId: v.routeId,
        status: v.status,
        bearing: v.bearing,
        speedKmh: v.speedKmh,
        cargoPriority: v.cargoPriority,
      },
      geometry: {
        type: 'Point',
        coordinates: [v.longitude, v.latitude],
      },
    }));

    source.setData({
      type: 'FeatureCollection',
      features,
    });
  }

  ngOnDestroy(): void {
    this.currentPopup?.remove();
    this.map?.remove();
  }
}
