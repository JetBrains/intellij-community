from abc import ABC, abstractmethod
from contextlib import AbstractContextManager
from typing import Any, BinaryIO

from rasterio.errors import OpenerRegistrationError as OpenerRegistrationError

class FileContainer(ABC):
    @abstractmethod
    def open(self, path: str, mode: str = "r", **kwds: Any) -> BinaryIO: ...
    @abstractmethod
    def isdir(self, path: str) -> bool: ...
    @abstractmethod
    def isfile(self, path: str) -> bool: ...
    @abstractmethod
    def ls(self, path: str) -> list[str]: ...
    @abstractmethod
    def mtime(self, path: str) -> int: ...
    @abstractmethod
    def rm(self, path: str) -> None: ...
    @abstractmethod
    def size(self, path: str) -> int: ...

class MultiByteRangeResource(ABC):
    @abstractmethod
    def get_byte_ranges(self, offsets: list[int], sizes: list[int]) -> list[bytes]: ...

class MultiByteRangeResourceContainer(FileContainer):
    @abstractmethod
    def open(self, path: str, **kwds: Any) -> MultiByteRangeResource: ...  # type: ignore[override]

# Duck-typed adapter: `obj` may be a `FileContainer` subclass or any
# object exposing the fsspec filesystem protocol (`hasattr(obj, "file_size")`).
def to_pyopener(obj: Any) -> FileContainer: ...

# `obj` accepts the same types as `to_pyopener` plus raw callables.
def _opener_registration(urlpath: str, obj: Any) -> AbstractContextManager[str]: ...
