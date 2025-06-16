from _typeshed import Incomplete
from typing import Literal
from typing_extensions import TypeAlias

import numpy as np
from numpy.typing import NDArray

from ..array import GeometryArray
from ..geodataframe import GeoDataFrame

_PATable: TypeAlias = Incomplete
_PAField: TypeAlias = Incomplete
_PAArray: TypeAlias = Incomplete

# Literal for language server completions and str because runtime normalizes to lowercase
_GeomEncoding: TypeAlias = Literal["WKB", "geoarrow"] | str  # noqa: Y051

GEOARROW_ENCODINGS: list[str]

class ArrowTable:
    def __init__(self, pa_table: _PATable) -> None: ...
    def __arrow_c_stream__(self, requested_schema=None): ...

class GeoArrowArray:
    def __init__(self, pa_field: _PAField, pa_array: _PAArray) -> None: ...
    def __arrow_c_array__(self, requested_schema=None) -> tuple[Incomplete, Incomplete]: ...

def geopandas_to_arrow(
    df: GeoDataFrame,
    index: bool | None = None,
    geometry_encoding: _GeomEncoding = "WKB",
    interleaved: bool = True,
    include_z: bool | None = None,
) -> tuple[_PATable, dict[str, str]]: ...
def construct_wkb_array(
    shapely_arr: NDArray[np.object_], *, field_name: str = "geometry", crs: str | None = None
) -> tuple[_PAField, _PAArray]: ...
def construct_geometry_array(
    shapely_arr: NDArray[np.object_],
    include_z: bool | None = None,
    *,
    field_name: str = "geometry",
    crs: str | None = None,
    interleaved: bool = True,
) -> tuple[_PAField, _PAArray]: ...
def arrow_to_geopandas(table, geometry: str | None = None) -> GeoDataFrame: ...
def arrow_to_geometry_array(arr) -> GeometryArray: ...
def construct_shapely_array(arr: _PAArray, extension_name: str) -> NDArray[np.object_]: ...
