from typing import TypeVar

_FileT = TypeVar("_FileT")

class FilePathBase:
    # Cython base for FilePath; constructor and overall surface are
    # implementation details — see `rasterio.io.FilePath` for the public API.
    def __init__(self, *args: object, **kwargs: object) -> None: ...
    def close(self) -> None: ...
    def exists(self) -> bool: ...

# Clones any file-like object (BytesIO, MemoryFile, fsspec file, Python
# file object); the returned object has the same concrete type as `fobj`.
def clone_file_obj(fobj: _FileT) -> _FileT: ...
