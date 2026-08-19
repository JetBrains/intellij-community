from collections.abc import Callable, Mapping, Sequence
from enum import Enum
from typing import Any, BinaryIO, Protocol, TypeAlias, type_check_only

from rasterio.crs import CRS
from rasterio.io import DatasetReaderBase, MemoryFile
from rasterio.windows import Window

# `DatasetReaderBase` covers every readable dataset handle: DatasetReader,
# DatasetWriter, BufferedDatasetWriter, MemoryDataset, and WarpedVRT (via
# WarpedVRTReaderBase). `MemoryFile` is a file wrapper, not a dataset.
AnyDataset: TypeAlias = DatasetReaderBase | MemoryFile

@type_check_only
class _SupportsGeoInterface(Protocol):
    @property
    def __geo_interface__(self) -> Mapping[str, Any]: ...

# A GeoJSON-like mapping, or any object exposing one through the
# `__geo_interface__` protocol (e.g. shapely / geopandas geometries).
# The runtime unwraps `__geo_interface__` before use, so both forms are
# accepted anywhere a geometry is expected.
Geometry: TypeAlias = Mapping[str, Any] | _SupportsGeoInterface  # noqa: Y047
Colormap: TypeAlias = dict[int, tuple[int, int, int] | tuple[int, int, int, int]]
CRSInput: TypeAlias = str | dict[str, str] | CRS
FileOrBytes: TypeAlias = BinaryIO | bytes
Indexes: TypeAlias = int | Sequence[int]
NumType: TypeAlias = int | float
ShapeND: TypeAlias = Sequence[int]
WindowInput: TypeAlias = Window | tuple[tuple[int, int], tuple[int, int]]

# Scalar values accepted by every GDAL CSL-style option list: global
# config (`set_gdal_config` / `Env`), per-call warp options
# (NUM_THREADS, INIT_DEST, …), RPC/transformer options (RPC_HEIGHT,
# RPC_DEM, COORDINATE_OPERATION, …), and metadata tag values. The
# runtime stringifies each value at the C boundary and does not
# special-case Enum or tuple types here (use `_OpenOption` for those).
_GDALOption: TypeAlias = str | int | float | bool | None  # noqa: Y047

# GDAL driver-specific open/creation option values. The runtime coerces
# every value to a string at the C boundary; documented usage covers
# scalars, Enum members (encoded as `.name.upper()`), and tuples of
# scalars (joined with commas). Lists are not handled specially — pass
# a tuple if you need a multi-value option.
_OpenOption: TypeAlias = str | int | float | bool | Enum | tuple[str | int | float | bool, ...] | None  # noqa: Y047

# Opaque OGR geometry handle (a Cython-wrapped C object). Callers only
# pass it back to other Cython internals; the public API surfaces
# already-decoded GeoJSON-like dicts.
_OGRGeometry: TypeAlias = Any  # noqa: Y047

# Scalar or arbitrarily nested list of scalars; used by helpers that
# recurse into sequences while preserving the nesting depth.
_NestedScalar: TypeAlias = float | list[_NestedScalar]  # noqa: Y047

# fsspec-style opener forwarded to `rasterio.open(opener=...)`:
# `(path: str, mode: str) -> file-like`.
_Opener: TypeAlias = Callable[..., Any]  # noqa: Y047
