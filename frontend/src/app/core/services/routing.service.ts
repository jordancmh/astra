import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RouteCalculateRequest, RouteCalculateResponse } from '../models/route.model';

@Injectable({
  providedIn: 'root',
})
export class RoutingService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  calculateRoute(request: RouteCalculateRequest): Observable<RouteCalculateResponse> {
    return this.http.post<RouteCalculateResponse>(
      `${this.baseUrl}/v1/routes/calculate`,
      request
    );
  }
}
