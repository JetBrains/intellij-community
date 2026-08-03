from collections.abc import Sequence
from decimal import Decimal
from typing import Literal, TypeAlias

from geojson.base import GeoJSON

_InputCoord: TypeAlias = float | Decimal | Geometry | Sequence[_InputCoord]
_CleanCoord: TypeAlias = float | Decimal | list[_CleanCoord]

DEFAULT_PRECISION: Literal[6]

class Geometry(GeoJSON):
    def __init__(
        self,
        coordinates: None | Sequence[_InputCoord] | Geometry = None,
        validate: bool = False,
        precision: None | int = None,
        **extra,
    ) -> None: ...
    @classmethod
    def clean_coordinates(cls, coords: Sequence[_InputCoord] | Geometry, precision: int) -> list[_CleanCoord]: ...

class GeometryCollection(GeoJSON):
    def __init__(self, geometries: Sequence[Geometry] | None = None, **extra) -> None: ...
    def errors(self) -> list[str] | None: ...
    def __getitem__(self, key) -> Geometry | tuple[()] | None: ...

def check_point(coord) -> str | None: ...

class Point(Geometry):
    def errors(self) -> list[str] | None: ...

class MultiPoint(Geometry):
    def errors(self) -> list[str] | None: ...

def check_line_string(coord) -> str | None: ...

class LineString(Geometry):
    def errors(self) -> list[str] | None: ...

class MultiLineString(MultiPoint):
    def errors(self) -> list[str] | None: ...

def check_polygon(coord) -> str | None: ...

class Polygon(Geometry):
    def errors(self) -> list[str] | None: ...

class MultiPolygon(Geometry):
    def errors(self) -> list[str] | None: ...

class Default: ...
