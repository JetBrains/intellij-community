import os
from collections.abc import Iterator, Sequence
from typing import Any, BinaryIO, Final
from typing_extensions import Self, deprecated

import numpy as np
from numpy.typing import DTypeLike, NDArray
from rasterio._affine_types import Affine
from rasterio._base import DatasetBase
from rasterio._path import _ParsedPath, _UnparsedPath
from rasterio._typing import Colormap, CRSInput, Indexes, NumType, ShapeND, WindowInput, _GDALOption, _OpenOption
from rasterio.control import GroundControlPoint
from rasterio.enums import Resampling
from rasterio.rpc import RPC

def validate_resampling(resampling: Resampling) -> None: ...
def virtual_file_to_buffer(filename: str) -> bytes: ...
def _is_complex_int(dtype: DTypeLike) -> bool: ...
def _getnpdtype(dtype: DTypeLike) -> np.dtype[Any]: ...
def _gdal_typename(dt: DTypeLike) -> str: ...
def _get_gdal_dtype(type_name: DTypeLike) -> int: ...
def _boundless_vrt_doc(
    src_dataset: DatasetBase,
    nodata: float | None = None,
    background: float | None = None,
    hidenodata: bool = False,
    width: int | None = None,
    height: int | None = None,
    transform: Affine | None = None,
    masked: bool = False,
    resampling: Resampling = ...,
) -> str: ...
def sample_gen(
    dataset: DatasetBase, xy: Sequence[tuple[float, float]], indexes: Indexes | None = None, masked: bool = False
) -> Iterator[NDArray[Any]]: ...

class Statistics:
    min: float
    max: float
    mean: float
    std: float
    def __init__(self, min: float, max: float, mean: float, std: float) -> None: ...

class DatasetReaderBase(DatasetBase):
    def read(
        self,
        indexes: Indexes | None = None,
        out: NDArray[Any] | None = None,
        window: WindowInput | None = None,
        masked: bool = False,
        out_shape: ShapeND | None = None,
        boundless: bool = False,
        resampling: Resampling = ...,
        fill_value: NumType | None = None,
        out_dtype: DTypeLike | None = None,
    ) -> NDArray[Any]: ...
    def read_masks(
        self,
        indexes: Indexes | None = None,
        out: NDArray[Any] | None = None,
        out_shape: ShapeND | None = None,
        window: WindowInput | None = None,
        boundless: bool = False,
        resampling: Resampling = ...,
    ) -> NDArray[Any]: ...
    def dataset_mask(
        self,
        out: NDArray[Any] | None = None,
        out_shape: ShapeND | None = None,
        window: WindowInput | None = None,
        boundless: bool = False,
        resampling: Resampling = ...,
    ) -> NDArray[Any]: ...
    def sample(
        self, xy: Sequence[tuple[float, float]], indexes: Indexes | None = None, masked: bool = False
    ) -> Iterator[NDArray[Any]]: ...
    def stats(self, *, indexes: Indexes | None = None, approx: bool = False) -> list[Statistics]: ...
    @deprecated("DatasetReaderBase.statistics() will be removed in 2.0.0; please switch to stats().")
    def statistics(self, bidx: int, approx: bool = False, clear_cache: bool = False) -> Statistics: ...

class MemoryFileBase:
    name: str
    mode: str
    closed: bool
    def __init__(
        self,
        file_or_bytes: bytes | BinaryIO | None = None,
        dirname: str | None = None,
        filename: str | None = None,
        ext: str = "",
    ) -> None: ...
    def __len__(self) -> int: ...
    def exists(self) -> bool: ...
    def getbuffer(self) -> memoryview: ...
    def close(self) -> None: ...
    def seek(self, offset: int, whence: int = 0) -> int: ...
    def tell(self) -> int: ...
    def read(self, size: int = -1) -> bytes: ...
    def write(self, data: bytes) -> int: ...

class DatasetWriterBase(DatasetReaderBase):
    name: str
    mode: str
    width: int
    height: int
    shape: tuple[int, int]
    driver: str

    def __init__(
        self,
        path: str | os.PathLike[str] | _ParsedPath | _UnparsedPath,
        mode: str,
        driver: str | None = None,
        width: int | None = None,
        height: int | None = None,
        count: int | None = None,
        crs: CRSInput | None = None,
        transform: Affine | None = None,
        dtype: DTypeLike | None = None,
        nodata: float | None = None,
        gcps: Sequence[GroundControlPoint] | None = None,
        rpcs: RPC | None = None,
        sharing: bool = False,
        **kwargs: _OpenOption,
    ) -> None: ...
    def write(
        self, arr: NDArray[Any], indexes: Indexes | None = None, window: WindowInput | None = None, masked: bool = False
    ) -> None: ...
    def write_band(self, bidx: int, src: NDArray[Any], window: WindowInput | None = None) -> None: ...
    def update_tags(self, bidx: int = 0, ns: str | None = None, **kwargs: _GDALOption) -> None: ...
    def set_band_description(self, bidx: int, value: str) -> None: ...
    def set_band_unit(self, bidx: int, value: str) -> None: ...
    def write_colormap(self, bidx: int, colormap: Colormap) -> None: ...
    def write_mask(self, mask_array: NDArray[Any], window: WindowInput | None = None) -> None: ...
    def build_overviews(self, factors: Sequence[int], resampling: Resampling = ...) -> None: ...
    def update_stats(
        self, *, stats: Sequence[Statistics] | None = None, indexes: Indexes | None = None, approx: bool = False
    ) -> None: ...
    def clear_stats(self) -> None: ...

class MemoryDataset(DatasetWriterBase):
    def __init__(
        self,
        image: NDArray[Any] | None = None,
        dtype: DTypeLike | None = None,
        count: int = 1,
        width: int | None = None,
        height: int | None = None,
        transform: Affine | None = None,
        gcps: Sequence[GroundControlPoint] | None = None,
        rpcs: RPC | None = None,
        crs: CRSInput | None = None,
    ) -> None: ...
    def __enter__(self) -> Self: ...

class BufferedDatasetWriterBase(DatasetWriterBase):
    def __init__(
        self,
        path: str | os.PathLike[str] | _ParsedPath | _UnparsedPath,
        mode: str = "w",
        driver: str | None = None,
        width: int | None = None,
        height: int | None = None,
        count: int | None = None,
        crs: CRSInput | None = None,
        transform: Affine | None = None,
        dtype: DTypeLike | None = None,
        nodata: float | None = None,
        gcps: Sequence[GroundControlPoint] | None = None,
        rpcs: RPC | None = None,
        sharing: bool = False,
        **kwargs: _OpenOption,
    ) -> None: ...
    def stop(self) -> None: ...

int8: Final[str]
uint8: Final[str]
