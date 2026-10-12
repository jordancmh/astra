package com.astra.engine.routing;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/routes", "/api/routes"})
public class RouteController {

    private final RoutingService routingService;

    public RouteController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @PostMapping("/calculate")
    public ResponseEntity<RouteCalculateResponse> calculateRoute(@Valid @RequestBody RouteCalculateRequest request) {
        RouteCalculateResponse response = routingService.calculateRoute(request);
        return ResponseEntity.ok(response);
    }
}
