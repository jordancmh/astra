import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { RoutingService } from './core/services/routing.service';
import mapboxgl from 'mapbox-gl';
import { of } from 'rxjs';

describe('App', () => {
  beforeEach(async () => {
    spyOn(mapboxgl, 'Map').and.returnValue({
      addControl: jasmine.createSpy('addControl'),
      on: jasmine.createSpy('on'),
      remove: jasmine.createSpy('remove'),
      addSource: jasmine.createSpy('addSource'),
      getSource: jasmine.createSpy('getSource').and.returnValue(undefined),
      addLayer: jasmine.createSpy('addLayer'),
      getLayer: jasmine.createSpy('getLayer').and.returnValue(undefined),
      fitBounds: jasmine.createSpy('fitBounds'),
    } as any);
    spyOn(mapboxgl, 'NavigationControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'AttributionControl').and.returnValue({} as any);
    spyOn(mapboxgl, 'ScaleControl').and.returnValue({} as any);

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        {
          provide: RoutingService,
          useValue: {
            calculateRoute: () => of({
              routeId: 'RT-001',
              status: 'OPTIMAL',
              distanceMeters: 1000,
              estimatedTimeMs: 100000,
              geometry: { type: 'LineString', coordinates: [] },
            }),
          },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the dashboard shell', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-dashboard-shell')).toBeTruthy();
  });
});
