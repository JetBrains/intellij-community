import logging
import os
from collections.abc import Sequence
from typing import Any, Final

from numpy.typing import DTypeLike, NDArray
from rasterio._affine_types import Affine
from rasterio.enums import Resampling
from rasterio.io import DatasetReaderBase

logger: Final[logging.Logger]

def stack(
    sources: Sequence[DatasetReaderBase | str | os.PathLike[str]],
    bounds: tuple[float, float, float, float] | None = None,
    res: float | tuple[float, float] | None = None,
    nodata: float | None = None,
    dtype: DTypeLike | None = None,
    indexes: int | Sequence[int] | None = None,
    output_count: int | None = None,
    resampling: Resampling = ...,
    target_aligned_pixels: bool = False,
    mem_limit: int = 64,
    use_highest_res: bool = False,
    masked: bool = False,
    dst_path: str | os.PathLike[str] | None = None,
    dst_kwds: dict[str, Any] | None = None,
) -> tuple[NDArray[Any], Affine]: ...
