import logging
import os
from collections.abc import Callable, Sequence
from typing import Any, Final, Literal, TypeAlias, overload
from typing_extensions import deprecated

from numpy.typing import DTypeLike, NDArray
from rasterio._affine_types import Affine
from rasterio.enums import Resampling
from rasterio.io import DatasetReaderBase

logger: Final[logging.Logger]

MethodFunction: TypeAlias = Callable[..., None]
MERGE_METHODS: Final[dict[str, MethodFunction]]

_Arr: TypeAlias = NDArray[Any]

# `**kwargs` on each merge method accepts forwarded options from `merge()` (e.g. `index`); ignored otherwise.
def copy_first(merged_data: _Arr, new_data: _Arr, merged_mask: _Arr, new_mask: _Arr, **kwargs: Any) -> None: ...
def copy_last(merged_data: _Arr, new_data: _Arr, merged_mask: _Arr, new_mask: _Arr, **kwargs: Any) -> None: ...
def copy_min(merged_data: _Arr, new_data: _Arr, merged_mask: _Arr, new_mask: _Arr, **kwargs: Any) -> None: ...
def copy_max(merged_data: _Arr, new_data: _Arr, merged_mask: _Arr, new_mask: _Arr, **kwargs: Any) -> None: ...
def copy_sum(merged_data: _Arr, new_data: _Arr, merged_mask: _Arr, new_mask: _Arr, **kwargs: Any) -> None: ...
def copy_count(merged_data: _Arr, new_data: _Arr, merged_mask: _Arr, new_mask: _Arr, **kwargs: Any) -> None: ...

@overload
def merge(
    sources: Sequence[DatasetReaderBase | str | os.PathLike[str]],
    bounds: tuple[float, float, float, float] | None = None,
    res: float | tuple[float, float] | None = None,
    nodata: float | None = None,
    dtype: DTypeLike | None = None,
    *,
    indexes: int | Sequence[int] | None = None,
    output_count: int | None = None,
    resampling: Resampling = ...,
    method: Literal["first", "last", "min", "max", "sum", "count"] | MethodFunction = "first",
    target_aligned_pixels: bool = False,
    mem_limit: int = 64,
    use_highest_res: bool = False,
    masked: bool = False,
    dst_path: str | os.PathLike[str] | None = None,
    dst_kwds: dict[str, Any] | None = None,
) -> tuple[NDArray[Any], Affine]: ...
@overload
@deprecated("The `precision` parameter is unused since rasterio 1.3 and will be removed in 2.0.0.")
def merge(
    sources: Sequence[DatasetReaderBase | str | os.PathLike[str]],
    bounds: tuple[float, float, float, float] | None = None,
    res: float | tuple[float, float] | None = None,
    nodata: float | None = None,
    dtype: DTypeLike | None = None,
    precision: int | None = None,
    indexes: int | Sequence[int] | None = None,
    output_count: int | None = None,
    resampling: Resampling = ...,
    method: Literal["first", "last", "min", "max", "sum", "count"] | MethodFunction = "first",
    target_aligned_pixels: bool = False,
    mem_limit: int = 64,
    use_highest_res: bool = False,
    masked: bool = False,
    dst_path: str | os.PathLike[str] | None = None,
    dst_kwds: dict[str, Any] | None = None,
) -> tuple[NDArray[Any], Affine]: ...
