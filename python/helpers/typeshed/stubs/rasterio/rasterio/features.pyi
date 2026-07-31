import logging
import os
from collections.abc import Iterable, Iterator
from typing import Any, Final, overload
from typing_extensions import deprecated

import numpy as np
from numpy.typing import DTypeLike, NDArray
from rasterio._affine_types import Affine
from rasterio._typing import Geometry as Geometry
from rasterio.enums import MergeAlg as MergeAlg
from rasterio.io import DatasetReaderBase
from rasterio.windows import Window as Window

log: Final[logging.Logger]

def geometry_mask(
    geometries: Iterable[Geometry], out_shape: tuple[int, int], transform: Affine, all_touched: bool = False, invert: bool = False
) -> NDArray[np.bool_]: ...
def shapes(
    source: NDArray[Any], mask: NDArray[np.bool_] | None = None, connectivity: int = 4, transform: Affine = ...
) -> Iterator[tuple[dict[str, Any], float | int]]: ...
def sieve(
    source: NDArray[Any], size: int, out: NDArray[Any] | None = None, mask: NDArray[np.bool_] | None = None, connectivity: int = 4
) -> NDArray[Any]: ...
def rasterize(
    shapes: Iterable[tuple[Geometry, float] | Geometry],
    out_shape: tuple[int, int] | None = None,
    fill: float = 0,
    nodata: float | None = None,
    masked: bool = False,
    out: NDArray[Any] | None = None,
    transform: Affine = ...,
    all_touched: bool = False,
    merge_alg: MergeAlg = ...,
    default_value: float = 1,
    dtype: DTypeLike | None = None,
    skip_invalid: bool = True,
    dst_path: str | os.PathLike[str] | None = None,
    dst_kwds: dict[str, Any] | None = None,
) -> NDArray[Any]: ...
def bounds(geometry: Geometry, north_up: bool = True, transform: Affine | None = None) -> tuple[float, float, float, float]: ...

@overload
def geometry_window(
    dataset: DatasetReaderBase, shapes: Iterable[Geometry], pad_x: float = 0, pad_y: float = 0, *, boundless: bool = False
) -> Window: ...
@overload
@deprecated(
    "`north_up`, `rotated`, and `pixel_precision` on features.geometry_window are "
    "unused since rasterio 1.2.1 and will be removed in a future release."
)
def geometry_window(
    dataset: DatasetReaderBase,
    shapes: Iterable[Geometry],
    pad_x: float = 0,
    pad_y: float = 0,
    north_up: bool | None = None,
    rotated: bool | None = None,
    pixel_precision: float | None = None,
    boundless: bool = False,
) -> Window: ...

def is_valid_geom(geom: Geometry) -> bool: ...
def dataset_features(
    src: DatasetReaderBase,
    bidx: int | None = None,
    sampling: int = 1,
    band: bool = True,
    as_mask: bool = False,
    with_nodata: bool = False,
    geographic: bool = True,
    precision: int = -1,
) -> Iterator[dict[str, Any]]: ...
