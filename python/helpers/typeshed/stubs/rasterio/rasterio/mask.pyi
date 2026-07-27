import logging
from collections.abc import Iterable
from typing import Any, Final

from numpy.typing import NDArray
from rasterio._affine_types import Affine
from rasterio._typing import Geometry
from rasterio.errors import WindowError as WindowError
from rasterio.features import geometry_mask as geometry_mask, geometry_window as geometry_window
from rasterio.io import DatasetReaderBase

logger: Final[logging.Logger]

def raster_geometry_mask(
    dataset: DatasetReaderBase,
    shapes: Iterable[Geometry],
    all_touched: bool = False,
    invert: bool = False,
    crop: bool = False,
    pad: bool = False,
    pad_width: float = 0.5,
) -> tuple[NDArray[Any], Affine, tuple[int, int, int, int]]: ...
def mask(
    dataset: DatasetReaderBase,
    shapes: Iterable[Geometry],
    all_touched: bool = False,
    invert: bool = False,
    nodata: float | None = None,
    filled: bool = True,
    crop: bool = False,
    pad: bool = False,
    pad_width: float = 0.5,
    indexes: int | Iterable[int] | None = None,
) -> tuple[NDArray[Any], Affine]: ...
