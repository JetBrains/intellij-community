import logging
from types import TracebackType
from typing import Any, Final
from typing_extensions import Self, deprecated

from numpy.typing import DTypeLike
from rasterio._affine_types import Affine
from rasterio._filepath import FilePathBase as FilePathBase
from rasterio._io import (
    BufferedDatasetWriterBase as BufferedDatasetWriterBase,
    DatasetReaderBase as DatasetReaderBase,
    DatasetWriterBase as DatasetWriterBase,
    MemoryFileBase as MemoryFileBase,
)
from rasterio._typing import CRSInput, FileOrBytes, _OpenOption
from rasterio.transform import TransformMethodsMixin
from rasterio.windows import WindowMethodsMixin

log: Final[logging.Logger]

class DatasetReader(DatasetReaderBase, WindowMethodsMixin, TransformMethodsMixin): ...
class DatasetWriter(DatasetWriterBase, WindowMethodsMixin, TransformMethodsMixin): ...
class BufferedDatasetWriter(BufferedDatasetWriterBase, WindowMethodsMixin, TransformMethodsMixin): ...

class MemoryFile(MemoryFileBase):
    def __init__(
        self, file_or_bytes: FileOrBytes | None = None, dirname: str | None = None, filename: str | None = None, ext: str = ".tif"
    ) -> None: ...
    def open(
        self,
        driver: str | None = None,
        width: int | None = None,
        height: int | None = None,
        count: int | None = None,
        crs: CRSInput | None = None,
        transform: Affine | None = None,
        dtype: DTypeLike | None = None,
        nodata: float | None = None,
        sharing: bool = False,
        thread_safe: bool = False,
        **kwargs: _OpenOption,
    ) -> DatasetReader | DatasetWriter: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_value: BaseException | None, traceback: TracebackType | None
    ) -> bool | None: ...

class ZipMemoryFile(MemoryFile):
    def __init__(self, file_or_bytes: FileOrBytes | None = None) -> None: ...
    def open(  # type: ignore[override]
        self, path: str, driver: str | None = None, sharing: bool = False, thread_safe: bool = False, **kwargs: _OpenOption
    ) -> DatasetReader: ...

@deprecated("FilePath is supplanted by rasterio.open's `opener` keyword argument and will be removed in 2.0.0.")
class FilePath(FilePathBase):
    # `filelike_obj`: any Python file-like object (BytesIO, fsspec file, etc.).
    def __init__(self, filelike_obj: Any, dirname: str | None = None, filename: str | None = None) -> None: ...
    def open(
        self, driver: str | None = None, sharing: bool = False, thread_safe: bool = False, **kwargs: _OpenOption
    ) -> DatasetReader: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_value: BaseException | None, traceback: TracebackType | None
    ) -> bool | None: ...

def get_writer_for_driver(driver: str) -> type[DatasetWriter | BufferedDatasetWriter] | None: ...
def get_writer_for_path(path: str, driver: str | None = None) -> type[DatasetWriter | BufferedDatasetWriter] | None: ...
