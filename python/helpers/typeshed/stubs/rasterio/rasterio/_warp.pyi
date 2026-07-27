from collections.abc import Sequence
from typing import Any, Final

from numpy.typing import DTypeLike, NDArray
from rasterio._affine_types import Affine
from rasterio._io import DatasetReaderBase
from rasterio._typing import CRSInput, Geometry, Indexes, ShapeND, WindowInput, _GDALOption, _NestedScalar
from rasterio.control import GroundControlPoint
from rasterio.crs import CRS
from rasterio.enums import Resampling
from rasterio.io import DatasetReader
from rasterio.rpc import RPC

SUPPORTED_RESAMPLING: Final[list[Resampling]]
DEFAULT_NODATA_FLAG: Final[object]

def recursive_round(val: _NestedScalar, precision: int) -> _NestedScalar: ...
def _transform_geom(
    src_crs: CRSInput, dst_crs: CRSInput, geom: Geometry | Sequence[Geometry], precision: int
) -> dict[str, Any] | list[dict[str, Any]]: ...
def _reproject(
    source: NDArray[Any] | Any,
    destination: NDArray[Any] | Any,
    src_transform: Affine | None = None,
    gcps: Sequence[GroundControlPoint] | None = None,
    rpcs: RPC | None = None,
    src_crs: CRSInput | None = None,
    src_nodata: float | None = None,
    dst_transform: Affine | None = None,
    dst_crs: CRSInput | None = None,
    dst_nodata: float | None = None,
    dst_alpha: int = 0,
    src_alpha: int = 0,
    resampling: Resampling = ...,
    init_dest_nodata: bool = True,
    tolerance: float = 0.125,
    num_threads: int = 1,
    warp_mem_limit: int = 0,
    working_data_type: int = 0,
    src_geoloc_array: NDArray[Any] | None = None,
    **kwargs: _GDALOption,
) -> tuple[NDArray[Any], Affine]: ...
def _calculate_default_transform(
    src_crs: CRSInput,
    dst_crs: CRSInput,
    width: int,
    height: int,
    left: float | None = None,
    bottom: float | None = None,
    right: float | None = None,
    top: float | None = None,
    gcps: Sequence[GroundControlPoint] | None = None,
    rpcs: RPC | None = None,
    src_geoloc_array: NDArray[Any] | None = None,
    **kwargs: _GDALOption,
) -> tuple[Affine, int, int]: ...
def _transform_bounds(
    src_crs: CRS, dst_crs: CRS, left: float, bottom: float, right: float, top: float, densify_pts: int
) -> tuple[float, float, float, float]: ...
def _suggested_proxy_vrt_doc(
    width: int,
    height: int,
    transform: Affine | None = None,
    crs: CRSInput | None = None,
    gcps: Sequence[GroundControlPoint] | None = None,
    rpcs: RPC | None = None,
) -> str: ...

class WarpedVRTReaderBase(DatasetReaderBase):
    src_dataset: DatasetReader
    src_crs: CRS
    src_transform: Affine | None
    resampling: Resampling
    tolerance: float
    src_nodata: float | None
    dst_nodata: float | None
    working_dtype: DTypeLike | None
    warp_extras: dict[str, _GDALOption]

    def __init__(
        self,
        src_dataset: DatasetReader,
        src_crs: CRSInput | None = None,
        crs: CRSInput | None = None,
        resampling: Resampling = ...,
        tolerance: float = 0.125,
        src_nodata: float | None = ...,
        nodata: float | None = ...,
        width: int | None = None,
        height: int | None = None,
        src_transform: Affine | None = None,
        transform: Affine | None = None,
        init_dest_nodata: bool = True,
        src_alpha: int = 0,
        dst_alpha: int = 0,
        add_alpha: bool = False,
        warp_mem_limit: int = 0,
        dtype: DTypeLike | None = None,
        **warp_extras: _GDALOption,
    ) -> None: ...
    def read(  # type: ignore[override]
        self,
        indexes: Indexes | None = None,
        out: NDArray[Any] | None = None,
        window: WindowInput | None = None,
        masked: bool = False,
        out_shape: ShapeND | None = None,
        resampling: Resampling = ...,
        fill_value: float | None = None,
        out_dtype: DTypeLike | None = None,
        # Swallows the deprecated `boundless` kwarg (raises ValueError if True).
        **kwargs: bool,
    ) -> NDArray[Any]: ...
    def read_masks(  # type: ignore[override]
        self,
        indexes: Indexes | None = None,
        out: NDArray[Any] | None = None,
        out_shape: ShapeND | None = None,
        window: WindowInput | None = None,
        resampling: Resampling = ...,
        # Swallows the deprecated `boundless` kwarg (raises ValueError if True).
        **kwargs: bool,
    ) -> NDArray[Any]: ...
