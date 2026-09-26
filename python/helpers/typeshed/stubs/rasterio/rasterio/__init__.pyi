import logging
import os
from collections.abc import Callable, Sequence
from typing import Any, Final, Literal, NamedTuple, TypeAlias, overload

from numpy.typing import DTypeLike, NDArray
from rasterio._base import DatasetBase as DatasetBase
from rasterio._io import Statistics as Statistics
from rasterio._path import _parse_path as _parse_path, _UnparsedPath as _UnparsedPath
from rasterio._show_versions import show_versions as show_versions
from rasterio._typing import AnyDataset, CRSInput, _Opener, _OpenOption
from rasterio._version import (
    gdal_version as gdal_version,
    get_geos_version as get_geos_version,
    get_proj_version as get_proj_version,
)
from rasterio._vsiopener import _opener_registration as _opener_registration
from rasterio.crs import CRS as CRS
from rasterio.drivers import driver_from_extension as driver_from_extension, is_blacklisted as is_blacklisted
from rasterio.dtypes import (
    bool_ as bool_,
    check_dtype as check_dtype,
    complex_ as complex_,
    complex_int16 as complex_int16,
    float16 as float16,
    float32 as float32,
    float64 as float64,
    int8 as int8,
    int16 as int16,
    int32 as int32,
    int64 as int64,
    sbyte as sbyte,
    ubyte as ubyte,
    uint8 as uint8,
    uint16 as uint16,
    uint32 as uint32,
    uint64 as uint64,
)
from rasterio.env import Env as Env, ensure_env_with_credentials as ensure_env_with_credentials
from rasterio.errors import (
    DriverCapabilityError as DriverCapabilityError,
    RasterioDeprecationWarning as RasterioDeprecationWarning,
    RasterioIOError as RasterioIOError,
)
from rasterio.io import (
    BufferedDatasetWriter as BufferedDatasetWriter,
    DatasetReader as DatasetReader,
    DatasetWriter as DatasetWriter,
    FilePath as FilePath,
    MemoryFile as MemoryFile,
    get_writer_for_driver as get_writer_for_driver,
    get_writer_for_path as get_writer_for_path,
)
from rasterio.profiles import default_gtiff_profile as default_gtiff_profile
from rasterio.transform import Affine as Affine, guard_transform as guard_transform

__all__ = ["CRS", "Band", "Env", "band", "open", "pad"]

__version__: Final[str]
__gdal_version__: Final[str]
__proj_version__: Final[str]
__geos_version__: Final[str]

have_vsi_plugin: Final[bool]
log: logging.Logger

_Fp: TypeAlias = str | os.PathLike[str] | MemoryFile | FilePath

@overload
def open(
    fp: _Fp,
    mode: Literal["r"] = "r",
    driver: str | Sequence[str] | None = None,
    width: int | None = None,
    height: int | None = None,
    count: int | None = None,
    crs: CRSInput | None = None,
    transform: Affine | None = None,
    dtype: DTypeLike | None = None,
    nodata: float | None = None,
    sharing: bool = False,
    thread_safe: bool = False,
    opener: _Opener | None = None,
    **kwargs: _OpenOption,
) -> DatasetReader: ...
@overload
def open(
    fp: _Fp,
    mode: Literal["r+", "w", "w+"],
    driver: str | Sequence[str] | None = None,
    width: int | None = None,
    height: int | None = None,
    count: int | None = None,
    crs: CRSInput | None = None,
    transform: Affine | None = None,
    dtype: DTypeLike | None = None,
    nodata: float | None = None,
    sharing: bool = False,
    thread_safe: bool = False,
    opener: _Opener | None = None,
    **kwargs: _OpenOption,
) -> DatasetWriter: ...
@overload
def open(
    fp: _Fp,
    mode: str = "r",
    driver: str | Sequence[str] | None = None,
    width: int | None = None,
    height: int | None = None,
    count: int | None = None,
    crs: CRSInput | None = None,
    transform: Affine | None = None,
    dtype: DTypeLike | None = None,
    nodata: float | None = None,
    sharing: bool = False,
    thread_safe: bool = False,
    opener: _Opener | None = None,
    **kwargs: _OpenOption,
) -> DatasetReader | DatasetWriter: ...

class Band(NamedTuple):
    ds: AnyDataset
    bidx: int | Sequence[int]
    dtype: str
    shape: tuple[int, ...]

def band(ds: AnyDataset, bidx: int | Sequence[int]) -> Band: ...

# `mode` and `**kwargs` mirror `numpy.pad`'s signature; see numpy.pad documentation.
def pad(
    array: NDArray[Any], transform: Affine, pad_width: int, mode: str | Callable[..., Any] | None = None, **kwargs: Any
) -> tuple[NDArray[Any], Affine]: ...
