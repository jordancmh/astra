import {
  Component,
  AfterViewInit,
  OnDestroy,
  ElementRef,
  ViewChild,
  ChangeDetectionStrategy,
} from '@angular/core';
import mapboxgl from 'mapbox-gl';
import { environment } from '../../../environments/environment';

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

    // Placeholder: vehicle markers, route LineStrings, and anomaly polygons
    // will be added as GeoJSONSource layers in future issues.
    this.map.on('load', () => {
      console.log('[ASTRA] Mapbox GL JS loaded — Klang Valley spatial viewport ready.');
    });
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }
}
