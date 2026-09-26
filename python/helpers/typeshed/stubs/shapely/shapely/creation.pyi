from collections.abc import Sequence
from typing import Literal, SupportsIndex, TypeAlias, overload
from typing_extensions import Unpack

import numpy as np
from numpy.typing import NDArray

from ._enum import ParamEnum
from ._geometry import GeometryType
from ._typing import ArrayLike, ArrayLikeSeq, GeoArray, OptGeoArrayLike, OptGeoArrayLikeSeq, UFuncKwargsNoOut
from .geometry import GeometryCollection, LinearRing, LineString, MultiLineString, MultiPoint, MultiPolygon, Point, Polygon
from .lib import Geometry

__all__ = [
    "box",
    "destroy_prepared",
    "empty",
    "geometrycollections",
    "linearrings",
    "linestrings",
    "multilinestrings",
    "multipoints",
    "multipolygons",
    "points",
    "polygons",
    "prepare",
]

class HandleNaN(ParamEnum):
    allow = 0
    skip = 1
    error = 2

_HandleNaN: TypeAlias = Literal["allow", "skip", "error"] | HandleNaN

@overload
def points(
    coords: float,
    y: float,
    z: float | None = None,
    indices: None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],  # acts as x
) -> Point: ...
@overload
def points(
    coords: Sequence[float],
    y: None = None,
    z: None = None,
    indices: None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],  # acts as x, y[, z]
) -> Point: ...
@overload
def points(
    coords: Sequence[float],  # acts as (x1, x2, ...)
    y: Sequence[float],  # must be (y1, y2, ...)
    z: Sequence[float] | None = None,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> GeoArray: ...
@overload
def points(
    coords: Sequence[Sequence[float]],  # acts as (x1, x2, ...), (y1, y2, ...)[, (z1, z2, ...)]
    y: None = None,
    z: None = None,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> GeoArray: ...
@overload
def points(
    coords: ArrayLike[float],
    y: ArrayLike[float],
    z: ArrayLike[float] | None = None,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> Point | GeoArray: ...
@overload
def points(
    coords: ArrayLikeSeq[float],
    y: ArrayLike[float] | None = None,
    z: ArrayLike[float] | None = None,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> Point | GeoArray: ...

@overload
def linestrings(
    coords: Sequence[float],  # acts as (x1, x2, ...)
    y: Sequence[float],
    z: Sequence[float] | None = None,
    indices: None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> LineString: ...
@overload
def linestrings(
    coords: Sequence[Sequence[float]],  # acts as (x1, y1[, z1]), (x2, y2[, z2]), ...
    y: None = None,
    z: None = None,
    indices: None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> LineString: ...
@overload
def linestrings(
    coords: Sequence[Sequence[Sequence[float]]],  # acts as seq of (x1, y1[, z1]), (x2, y2[, z2]), ...
    y: None = None,
    z: None = None,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> GeoArray: ...
@overload
def linestrings(
    coords: ArrayLikeSeq[float],
    y: ArrayLikeSeq[float] | None = None,
    z: ArrayLikeSeq[float] | None = None,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> LineString | GeoArray: ...

@overload
def linearrings(
    coords: Sequence[float],  # acts as (x1, x2, ...)
    y: Sequence[float],
    z: Sequence[float] | None = None,
    indices: None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> LinearRing: ...
@overload
def linearrings(
    coords: Sequence[Sequence[float]],  # acts as (x1, y1[, z1]), (x2, y2[, z2]), ...
    y: None = None,
    z: None = None,
    indices: None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> LinearRing: ...
@overload
def linearrings(
    coords: Sequence[Sequence[Sequence[float]]],  # acts as seq of (x1, y1[, z1]), (x2, y2[, z2]), ...
    y: None = None,
    z: None = None,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> GeoArray: ...
@overload
def linearrings(
    coords: ArrayLikeSeq[float],
    y: ArrayLikeSeq[float] | None = None,
    z: ArrayLikeSeq[float] | None = None,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    handle_nan: _HandleNaN = ...,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> LinearRing | GeoArray: ...

@overload
def polygons(
    geometries: LinearRing | Sequence[Sequence[float]] | None,
    holes: ArrayLikeSeq[float] | OptGeoArrayLikeSeq | None = None,
    indices: None = None,
    *,
    out: None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> Polygon: ...
@overload
def polygons(
    geometries: Sequence[LinearRing | Sequence[Sequence[float]] | None],
    holes: ArrayLikeSeq[float] | OptGeoArrayLikeSeq | None = None,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> GeoArray: ...
@overload
def polygons(
    geometries: ArrayLikeSeq[float] | OptGeoArrayLikeSeq,
    holes: ArrayLikeSeq[float] | OptGeoArrayLikeSeq | None = None,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> Polygon | GeoArray: ...

@overload
def box(xmin: float, ymin: float, xmax: float, ymax: float, ccw: bool = True, **kwargs: Unpack[UFuncKwargsNoOut]) -> Polygon: ...
@overload
def box(
    xmin: ArrayLikeSeq[float],
    ymin: ArrayLikeSeq[float],
    xmax: ArrayLikeSeq[float],
    ymax: ArrayLikeSeq[float],
    ccw: bool = True,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> GeoArray: ...

@overload
def multipoints(
    geometries: Sequence[Point | Sequence[float] | None],
    indices: None = None,
    *,
    out: None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> MultiPoint: ...
@overload
def multipoints(
    geometries: Sequence[Sequence[Point | Sequence[float] | None]],
    indices: ArrayLikeSeq[int] | None = None,
    *,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> GeoArray: ...
@overload
def multipoints(
    geometries: OptGeoArrayLikeSeq,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> MultiPoint | GeoArray: ...

@overload
def multilinestrings(
    geometries: Sequence[LineString | Sequence[Sequence[float]] | None],
    indices: None = None,
    *,
    out: None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> MultiLineString: ...
@overload
def multilinestrings(
    geometries: Sequence[Sequence[LineString | Sequence[Sequence[float]] | None]],
    indices: ArrayLikeSeq[int] | None = None,
    *,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> GeoArray: ...
@overload
def multilinestrings(
    geometries: OptGeoArrayLikeSeq,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> MultiLineString | GeoArray: ...

@overload
def multipolygons(
    geometries: Sequence[Polygon | Sequence[Sequence[float]] | None],
    indices: None = None,
    *,
    out: None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> MultiPolygon: ...
@overload
def multipolygons(
    geometries: Sequence[Sequence[Polygon | Sequence[Sequence[float]] | None]],
    indices: ArrayLikeSeq[int] | None = None,
    *,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> GeoArray: ...
@overload
def multipolygons(
    geometries: OptGeoArrayLikeSeq,
    indices: ArrayLikeSeq[int] | None = None,
    *,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> MultiPolygon | GeoArray: ...

@overload
def geometrycollections(
    geometries: Sequence[Geometry | None], indices: None = None, out: None = None, **kwargs: Unpack[UFuncKwargsNoOut]
) -> GeometryCollection: ...
@overload
def geometrycollections(
    geometries: Sequence[Sequence[Geometry | None]],
    indices: ArrayLikeSeq[int] | None = None,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> GeoArray: ...
@overload
def geometrycollections(
    geometries: OptGeoArrayLikeSeq,
    indices: ArrayLikeSeq[int] | None = None,
    out: NDArray[np.object_] | None = None,
    **kwargs: Unpack[UFuncKwargsNoOut],
) -> GeometryCollection | GeoArray: ...

def prepare(geometry: OptGeoArrayLike, **kwargs: Unpack[UFuncKwargsNoOut]) -> None: ...
def destroy_prepared(geometry: OptGeoArrayLike, **kwargs: Unpack[UFuncKwargsNoOut]) -> None: ...
def empty(
    shape: SupportsIndex | Sequence[SupportsIndex], geom_type: GeometryType | int | None = None, order: Literal["C", "F"] = "C"
) -> NDArray[np.object_]: ...
