import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MapViewComponent } from './map-view.component';
import { RoutingService } from '../../core/services/routing.service';
import { RouteCalculateResponse } from '../../core/models/route.model';
import mapboxgl from 'mapbox-gl';
import { of, throwError } from 'rxjs';

describe('MapViewComponent', () => {
  let component: MapViewComponent;
  let fixture: ComponentFixture<MapViewComponent>;
  let mockMap: any;
  let routingServiceSpy: jasmine.SpyObj<RoutingService>;

  const mockRouteResponse: RouteCalculateResponse = {
    routeId: 'RT-PROTOTYPE-01',
    status: 'OPTIMAL',
    distanceMeters: 18450.0,
    estimatedTimeMs: 1250000,
    geometry: {
      type: 'LineString',
      coordinates: [
        [101.6839, 3.1390],
        [101.6200, 3.1000],
        [101.5540, 3.0755],
      ],
    },
  };

  beforeEach(async () => {
    mockMap = {
      addControl: jasmine.createSpy('addControl'),
      on: jasmine.createSpy('on').and.callFake((event: string, callback: () => void) => {
        if (event === 'load') {
          callback();
        }
      }),
      remove: jasmine.createSpy('remove'),
      addSource: jasmine.createSpy('addSource'),
      getSource: jasmine.createSpy('getSource').and.returnValue(undefined),
      addLayer: jasmine.createSpy('addLayer'),
      getLayer: jasmine.createSpy('getLayer').and.returnValue(undefined),
      fitBounds: jasmine.createSpy('fitBounds'),
    };

    // Spy on mapboxgl constructors to bypass WebGL requirement in headless test runs
    spyOn(mapboxgl, 'Map').and.returnValue(mockMap);
    spyOn(mapboxgl, 'NavigationControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'AttributionControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'ScaleControl').and.returnValue({} as any);

    routingServiceSpy = jasmine.createSpyObj('RoutingService', ['calculateRoute']);
    routingServiceSpy.calculateRoute.and.returnValue(of(mockRouteResponse));

    await TestBed.configureTestingModule({
      imports: [MapViewComponent],
      providers: [
        { provide: RoutingService, useValue: routingServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MapViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize mapbox map with dark-v11 style and Klang Valley bounds', () => {
    expect(mapboxgl.Map).toHaveBeenCalledWith(
      jasmine.objectContaining({
        style: 'mapbox://styles/mapbox/dark-v11',
        center: [101.6839, 3.1390],
        zoom: 11,
        maxBounds: [
          [101.40, 2.90],
          [101.90, 3.30],
        ],
      })
    );
  });

  it('should add navigation, attribution, and scale controls', () => {
    expect(mockMap.addControl).toHaveBeenCalledTimes(3);
  });

  it('should automatically query prototype route on map load with expected coordinates', () => {
    expect(routingServiceSpy.calculateRoute).toHaveBeenCalledWith(
      jasmine.objectContaining({
        originLng: 101.6839,
        originLat: 3.1390,
        destLng: 101.5540,
        destLat: 3.0755,
      })
    );
  });

  it('should add active-route GeoJSON source and active-route-line cyan layer', () => {
    expect(mockMap.addSource).toHaveBeenCalledWith(
      'active-route',
      jasmine.objectContaining({
        type: 'geojson',
        data: mockRouteResponse.geometry,
      })
    );

    expect(mockMap.addLayer).toHaveBeenCalledWith(
      jasmine.objectContaining({
        id: 'active-route-line',
        type: 'line',
        source: 'active-route',
        paint: jasmine.objectContaining({
          'line-color': '#06b6d4',
          'line-width': 4,
        }),
      })
    );

    expect(mockMap.fitBounds).toHaveBeenCalled();
  });

  it('should update existing source when route source already exists', () => {
    const existingSourceMock = {
      setData: jasmine.createSpy('setData'),
    };
    mockMap.getSource.and.returnValue(existingSourceMock as any);

    // Call private method or trigger route load
    (component as any).renderRouteGeometry(mockRouteResponse.geometry);

    expect(existingSourceMock.setData).toHaveBeenCalledWith(mockRouteResponse.geometry);
  });

  it('should handle routing error gracefully without throwing', () => {
    routingServiceSpy.calculateRoute.and.returnValue(throwError(() => new Error('Backend offline')));
    spyOn(console, 'warn');

    expect(() => {
      (component as any).loadPrototypeRoute();
    }).not.toThrow();

    expect(console.warn).toHaveBeenCalledWith(
      '[ASTRA] Failed to load prototype route from backend:',
      jasmine.any(Error)
    );
  });

  it('should remove the map on destroy', () => {
    component.ngOnDestroy();
    expect(mockMap.remove).toHaveBeenCalled();
  });
});
