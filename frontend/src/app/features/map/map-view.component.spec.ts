import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { MapViewComponent } from './map-view.component';
import { RoutingService } from '../../core/services/routing.service';
import { AnomalyService } from '../../core/services/anomaly.service';
import { RouteCalculateResponse } from '../../core/models/route.model';
import { NetworkAnomaly } from '../../core/models/anomaly.model';
import mapboxgl from 'mapbox-gl';
import { of, throwError } from 'rxjs';

describe('MapViewComponent', () => {
  let component: MapViewComponent;
  let fixture: ComponentFixture<MapViewComponent>;
  let mockMap: any;
  let mockCanvas: any;
  let routingServiceSpy: jasmine.SpyObj<RoutingService>;
  let anomalyServiceSpy: jasmine.SpyObj<AnomalyService>;
  let registeredMapEventHandlers: { [key: string]: Function } = {};

  const activeAnomaliesSignal = signal<NetworkAnomaly[]>([]);
  const isDroppingOnMapSignal = signal<boolean>(false);

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

  const mockAnomaly: NetworkAnomaly = {
    id: 'D-001',
    anomalyType: 'ROAD_CLOSURE',
    severity: 'HIGH',
    radiusMeters: 600,
    center: [101.6210, 3.1180],
    polygon: {
      type: 'Polygon',
      coordinates: [
        [
          [101.615, 3.112],
          [101.627, 3.112],
          [101.627, 3.124],
          [101.615, 3.124],
          [101.615, 3.112],
        ],
      ],
    },
    injectedAt: '2026-10-12T08:34:12Z',
    isActive: true,
  };

  beforeEach(async () => {
    registeredMapEventHandlers = {};
    mockCanvas = { style: { cursor: '' } };
    activeAnomaliesSignal.set([]);
    isDroppingOnMapSignal.set(false);

    mockMap = {
      addControl: jasmine.createSpy('addControl'),
      on: jasmine.createSpy('on').and.callFake((...args: any[]) => {
        if (args.length === 2) {
          const [event, cb] = args;
          registeredMapEventHandlers[event] = cb;
          if (event === 'load') {
            cb();
          }
        } else if (args.length === 3) {
          const [event, layer, cb] = args;
          registeredMapEventHandlers[`${event}:${layer}`] = cb;
        }
      }),
      remove: jasmine.createSpy('remove'),
      addSource: jasmine.createSpy('addSource'),
      getSource: jasmine.createSpy('getSource').and.returnValue(undefined),
      addLayer: jasmine.createSpy('addLayer'),
      getLayer: jasmine.createSpy('getLayer').and.returnValue(undefined),
      fitBounds: jasmine.createSpy('fitBounds'),
      getCanvas: jasmine.createSpy('getCanvas').and.returnValue(mockCanvas),
    };

    spyOn(mapboxgl, 'Map').and.returnValue(mockMap);
    spyOn(mapboxgl, 'NavigationControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'AttributionControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'ScaleControl').and.returnValue({} as any);

    routingServiceSpy = jasmine.createSpyObj('RoutingService', ['calculateRoute']);
    routingServiceSpy.calculateRoute.and.returnValue(of(mockRouteResponse));

    anomalyServiceSpy = jasmine.createSpyObj('AnomalyService', [
      'fetchActiveAnomalies',
      'clearAnomaly',
      'setDroppingOnMap',
      'setSelectedCoordinates',
      'isWithinKlangValley',
    ]);
    (anomalyServiceSpy as any).activeAnomalies = activeAnomaliesSignal;
    (anomalyServiceSpy as any).isDroppingOnMap = isDroppingOnMapSignal;

    anomalyServiceSpy.fetchActiveAnomalies.and.returnValue(of([]));
    anomalyServiceSpy.clearAnomaly.and.returnValue(of(undefined));
    anomalyServiceSpy.isWithinKlangValley.and.callFake(
      (lng: number, lat: number) => lng >= 101.4 && lng <= 101.9 && lat >= 2.9 && lat <= 3.3
    );

    await TestBed.configureTestingModule({
      imports: [MapViewComponent],
      providers: [
        { provide: RoutingService, useValue: routingServiceSpy },
        { provide: AnomalyService, useValue: anomalyServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MapViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should add anomaly GeoJSON source and fill/line layers on load', () => {
    expect(mockMap.addSource).toHaveBeenCalledWith(
      'anomalies-source',
      jasmine.objectContaining({
        type: 'geojson',
      })
    );

    expect(mockMap.addLayer).toHaveBeenCalledWith(
      jasmine.objectContaining({
        id: 'anomalies-fill',
        type: 'fill',
        source: 'anomalies-source',
      })
    );

    expect(mockMap.addLayer).toHaveBeenCalledWith(
      jasmine.objectContaining({
        id: 'anomalies-line',
        type: 'line',
        source: 'anomalies-source',
      })
    );

    expect(anomalyServiceSpy.fetchActiveAnomalies).toHaveBeenCalled();
  });

  it('should update anomalies source when activeAnomalies signal updates', () => {
    const mockAnomalySource = {
      setData: jasmine.createSpy('setData'),
    };
    mockMap.getSource.and.callFake((id: string) => {
      if (id === 'anomalies-source') return mockAnomalySource;
      return undefined;
    });

    activeAnomaliesSignal.set([mockAnomaly]);
    fixture.detectChanges();

    expect(mockAnomalySource.setData).toHaveBeenCalledWith(
      jasmine.objectContaining({
        type: 'FeatureCollection',
        features: jasmine.arrayContaining([
          jasmine.objectContaining({
            id: 'D-001',
            type: 'Feature',
            geometry: mockAnomaly.polygon,
          }),
        ]),
      })
    );
  });

  it('should handle map click when dropping on map with valid coordinates', () => {
    isDroppingOnMapSignal.set(true);

    const clickHandler = registeredMapEventHandlers['click'];
    expect(clickHandler).toBeDefined();

    clickHandler({ lngLat: { lng: 101.6210, lat: 3.1180 } });

    expect(anomalyServiceSpy.setSelectedCoordinates).toHaveBeenCalledWith([101.6210, 3.1180]);
    expect(anomalyServiceSpy.setDroppingOnMap).toHaveBeenCalledWith(false);
  });

  it('should ignore map click when coordinates are outside Klang Valley', () => {
    isDroppingOnMapSignal.set(true);
    spyOn(console, 'warn');

    const clickHandler = registeredMapEventHandlers['click'];
    clickHandler({ lngLat: { lng: 100.0, lat: 5.0 } }); // Out of bounds

    expect(anomalyServiceSpy.setSelectedCoordinates).not.toHaveBeenCalled();
    expect(console.warn).toHaveBeenCalled();
  });

  it('should update cursor on mouseenter and mouseleave for anomalies-fill', () => {
    const enterHandler = registeredMapEventHandlers['mouseenter:anomalies-fill'];
    const leaveHandler = registeredMapEventHandlers['mouseleave:anomalies-fill'];

    expect(enterHandler).toBeDefined();
    expect(leaveHandler).toBeDefined();

    enterHandler();
    expect(mockCanvas.style.cursor).toBe('pointer');

    leaveHandler();
    expect(mockCanvas.style.cursor).toBe('');
  });

  it('should remove the map and current popup on destroy', () => {
    component.ngOnDestroy();
    expect(mockMap.remove).toHaveBeenCalled();
  });
});
