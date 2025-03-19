import sys
from _typeshed import StrPath
from collections.abc import Iterator, Sequence
from io import TextIOWrapper
from os import PathLike
from typing import IO, Literal, TypeVar, overload
from typing_extensions import Self, TypeAlias
from zipfile import ZipFile

_ReadWriteBinaryMode: TypeAlias = Literal["r", "w", "rb", "wb"]

_ZF = TypeVar("_ZF", bound=ZipFile)

if sys.version_info >= (3, 12):
    class InitializedState:
        def __init__(self, *args: object, **kwargs: object) -> None: ...
        def __getstate__(self) -> tuple[list[object], dict[object, object]]: ...
        def __setstate__(self, state: Sequence[tuple[list[object], dict[object, object]]]) -> None: ...

    class CompleteDirs(InitializedState, ZipFile):
        def resolve_dir(self, name: str) -> str: ...
        @overload
        @classmethod
        def make(cls, source: ZipFile) -> CompleteDirs: ...
        @overload
        @classmethod
        def make(cls, source: StrPath | IO[bytes]) -> Self: ...
        if sys.version_info >= (3, 13):
            @classmethod
            def inject(cls, zf: _ZF) -> _ZF: ...

    class Path:
        root: CompleteDirs
        def __init__(self, root: ZipFile | StrPath | IO[bytes], at: str = "") -> None: ...
        @property
        def name(self) -> str: ...
        @property
        def parent(self) -> PathLike[str]: ...  # undocumented
        if sys.version_info >= (3, 10):
            @property
            def filename(self) -> PathLike[str]: ...  # undocumented
        if sys.version_info >= (3, 11):
            @property
            def suffix(self) -> str: ...
            @property
            def suffixes(self) -> list[str]: ...
            @property
            def stem(self) -> str: ...

        if sys.version_info >= (3, 9):
            @overload
            def open(
                self,
                mode: Literal["r", "w"] = "r",
                encoding: str | None = None,
                errors: str | None = None,
                newline: str | None = None,
                line_buffering: bool = ...,
                write_through: bool = ...,
                *,
                pwd: bytes | None = None,
            ) -> TextIOWrapper: ...
            @overload
            def open(self, mode: Literal["rb", "wb"], *, pwd: bytes | None = None) -> IO[bytes]: ...
        else:
            def open(
                self, mode: _ReadWriteBinaryMode = "r", pwd: bytes | None = None, *, force_zip64: bool = False
            ) -> IO[bytes]: ...

        if sys.version_info >= (3, 10):
            def iterdir(self) -> Iterator[Self]: ...
        else:
            def iterdir(self) -> Iterator[Path]: ...

        def is_dir(self) -> bool: ...
        def is_dir(self) -> bool: ...
      elf, s: str, /) -> object: ...

class ZipFile:
    filename: str | None
    debug: int
    comment: bytes
    filelist: list[ZipInfo]
    fp: IO[bytes] | None
    NameToInfo: dict[str, ZipInfo]
    start_dir: int  # undocumented
    compression: int  # undocumented
    compresslevel: int | None  # undocumented
    mode: _ZipFileMode  # undocumented
    pwd: bytes | None  # undocumented
    if sys.version_info >= (3, 11):
        @overload
        def __init__(
            self,
            file: StrPath | IO[bytes],
            mode: Literal["r"] = "r",
            compression: int = 0,
            allowZip64: bool = True,
            compresslevel: int | None = None,
            *,
            strict_timestamps: bool = True,
            metadata_encoding: str | None,
        ) -> None: ...
        @overload
        def __init__(
            self,
            file: StrPath | IO[bytes],
            mode: _ZipFileMode = "r",
            compression: int = 0,
            allowZip64: bool = True,
            compresslevel: int | No