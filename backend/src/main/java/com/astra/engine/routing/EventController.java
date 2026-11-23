package com.astra.engine.routing;

import com.astra.engine.routing.NegotiationEvent;
import com.astra.engine.routing.NegotiationEventRepository;
import com.astra.engine.routing.dto.NegotiationEventResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/events", "/api/events"})
public class EventController {

    private final NegotiationEventRepository negotiationEventRepository;

    public EventController(NegotiationEventRepository negotiationEventRepository) {
        this.negotiationEventRepository = negotiationEventRepository;
    }

    @GetMapping
    public ResponseEntity<List<NegotiationEventResponse>> getRecentEvents(
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));

        List<NegotiationEvent> events;
        if (boundedLimit == 50) {
            events = negotiationEventRepository.findTop50ByOrderByCreatedAtDesc();
        } else {
            events = negotiationEventRepository.findAll(
                    PageRequest.of(0, boundedLimit, org.springframework.data.domain.Sort.by("createdAt").descending())
            ).getContent();
        }

        List<NegotiationEventResponse> response = events.stream()
                .map(e -> new NegotiationEventResponse(
                        e.getId(),
                        e.getAnomalyId(),
                        e.getVehicleId(),
                        e.getAffectedRoutes(),
                        e.getEventMessage(),
                        e.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }
}
