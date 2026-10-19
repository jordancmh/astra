import {
  Component,
  AfterViewInit,
  OnDestroy,
  ElementRef,
  ViewChild,
  ChangeDetectionStrategy,
  inject,
} from '@angular/core';
import mapboxgl from 'mapbox-gl';
import { environment } from '../../../environments/environment';
import { RoutingService } from '../../core/services/routing.service';
import { GeoJsonLineString, RouteCalculateRequest } from '../../core/models/route.model';

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
  private map!: mapboxgl.Map;

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
      this.loadPrototypeRoute();
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
    this.map?.remove();
  }
}
