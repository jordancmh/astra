"""
ASTRA Multi-Agent System (MAS) - Negotiation Package.
Implements peer-to-peer conflict negotiation protocols and right-of-way resolution (FR 3.2, FR 3.3).
"""
from .coordinator import NegotiationCoordinator
from .models import (
    CorridorType,
    DetourCorridor,
    NegotiationMessage,
    RightOfWayDecision,
)
from .protocol import (
    DEFAULT_PRIMARY_CORRIDOR,
    DEFAULT_SECONDARY_CORRIDOR,
    RightOfWayNegotiator,
)

__all__ = [
    "CorridorType",
    "DetourCorridor",
    "NegotiationMessage",
    "RightOfWayDecision",
    "RightOfWayNegotiator",
    "NegotiationCoordinator",
    "DEFAULT_PRIMARY_CORRIDOR",
    "DEFAULT_SECONDARY_CORRIDOR",
]
