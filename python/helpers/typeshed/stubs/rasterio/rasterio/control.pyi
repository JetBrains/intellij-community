from collections.abc import Sequence
from typing import Literal, TypedDict, type_check_only

@type_check_only
class GroundControlPointDict(TypedDict):
    id: str
    info: str | None
    row: float
    col: float
    x: float
    y: float
    z: float | None

@type_check_only
class GroundControlPointGeometry(TypedDict):
    type: Literal["Point"]
    coordinates: Sequence[float]

@type_check_only
class GroundControlPointFeature(TypedDict):
    id: str
    type: Literal["Feature"]
    geometry: GroundControlPointGeometry
    properties: GroundControlPointDict

class GroundControlPoint:
    id: str
    info: str | None
    row: float
    col: float
    x: float
    y: float
    z: float | None
    def __init__(
        self,
        row: float | None = None,
        col: float | None = None,
        x: float | None = None,
        y: float | None = None,
        z: float | None = None,
        id: str | None = None,
        info: str | None = None,
    ) -> None: ...
    def asdict(self) -> GroundControlPointDict: ...
    @property
    def __geo_interface__(self) -> GroundControlPointFeature: ...
