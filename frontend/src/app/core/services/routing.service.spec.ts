import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RoutingService } from './routing.service';
import { RouteCalculateRequest, RouteCalculateResponse } from '../models/route.model';
import { environment } from '../../../environments/environment';

describe('RoutingService', () => {
  let service: RoutingService;
  let httpTesting: HttpTestingController;

  const mockRequest: RouteCalculateRequest = {
    routeId: 'RT-001',
    vehicleId: 'V-001',
    originLat: 3.1390,
    originLng: 101.6839,
    destLat: 3.0755,
    destLng: 101.5540,
    avoidAnomalyIds: [],
  };

  const mockResponse: RouteCalculateResponse = {
    routeId: 'RT-001',
    status: 'OPTIMAL',
    distanceMeters: 18450.0,
    estimatedTimeMs: 1250000,
    geometry: {
      type: 'LineString',
      coordinates: [
        [101.6839, 3.1390],
        [101.5540, 3.0755],
      ],
    },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        RoutingService,
      ],
    });

    service = TestBed.inject(RoutingService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should send POST request to /v1/routes/calculate with request body', () => {
    service.calculateRoute(mockRequest).subscribe((res) => {
      expect(res).toEqual(mockResponse);
      expect(res.geometry.type).toBe('LineString');
      expect(res.geometry.coordinates.length).toBe(2);
    });

    const expectedUrl = `${environment.apiBaseUrl}/v1/routes/calculate`;
    const req = httpTesting.expectOne(expectedUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(mockRequest);

    req.flush(mockResponse);
  });

  it('should propagate HTTP error when backend calculation fails', () => {
    service.calculateRoute(mockRequest).subscribe({
      next: () => fail('Should have failed with 500 error'),
      error: (error) => {
        expect(error.status).toBe(500);
      },
    });

    const expectedUrl = `${environment.apiBaseUrl}/v1/routes/calculate`;
    const req = httpTesting.expectOne(expectedUrl);
    req.flush('Internal Server Error', { status: 500, statusText: 'Server Error' });
  });
});
