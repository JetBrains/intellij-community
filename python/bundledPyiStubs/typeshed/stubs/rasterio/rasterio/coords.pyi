from typing import NamedTuple, TypeAlias

_Quadruple: TypeAlias = tuple[float, float, float, float]

class BoundingBox(NamedTuple):
    left: float
    bottom: float
    right: float
    top: float

def disjoint_bounds(bounds1: BoundingBox | _Quadruple, bounds2: BoundingBox | _Quadruple) -> bool: ...
