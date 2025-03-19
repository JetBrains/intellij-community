from __future__ import annotations

import sys
from _typeshed import StrPath
from os import PathLike
from pathlib import Path
from zipfile import Path as ZipPath

if sys.version_info >= (3, 10):
    from importlib.metadata._meta import SimplePath

    # Simplified version of zipfile.Path
    class MyPath:
        @property
        def parent(self) -> PathLike[str]: ...  # undocumented

        def read_text(self, encoding: str | None = ..., errors: str | None = ...) -> str: ...
        def read_bytes(self) -> bytes: ...
        def joinpath(self, *other: StrPath) -> MyPath: ...
        def __truediv__(self, add: StrPath) -> MyPath: ...
        def exists(self) -> bool: ...

    def takes_simple_path(p: SimplePath) -> None: ...

    takes_simple_path(Path())
    takes_simple_path(ZipPath(""))
    takes_simple_path(MyPath())
    takes_simple_path("some string")  # type: ignore
