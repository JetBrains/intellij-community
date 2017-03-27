# Stubs for tarfile

from typing import (
    Callable, IO, Iterable, Iterator, List, Mapping, Optional, Type,
    Union,
)
import sys
from types import TracebackType


ENCODING = ...  # type: str

USTAR_FORMAT = ...  # type: int
GNU_FORMAT = ...  # type: int
PAX_FORMAT = ...  # type: int
DEFAULT_FORMAT = ...  # type: int

REGTYPE = ...  # type: bytes
AREGTYPE = ...  # type: bytes
LNKTYPE = ...  # type: bytes
SYMTYPE = ...  # type: bytes
DIRTYPE = ...  # type: bytes
FIFOTYPE = ...  # type: bytes
CONTTYPE = ...  # type: bytes
CHRTYPE = ...  # type: bytes
BLKTYPE = ...  # type: bytes
GNUTYPE_SPARSE = ...  # type: bytes

if sys.version_info < (3,):
    TAR_PLAIN = ...  # type: int
    TAR_GZIPPED = ...  # type: int

def open(name: Optional[str] = ..., mode: str = ...,
        fileobj: Optional[IO[bytes]] = ..., bufsize: int = ...,
        *, format: Optional[int] = ..., tarinfo: Optional[TarInfo] = ...,
        dereference: Optional[bool] = ...,
        ignore_zeros: Optional[bool] = ...,
        encoding: Optional[str] = ..., errors: str = ...,
        pax_headers: Optional[Mapping[str, str]] = ...,
        debug: Optional[int] = ...,
        errorlevel: Optional[int] = ...) -> TarFile: ...


class TarFile(Iterable[TarInfo]):
    name = ...  # type: Optional[str]
    mode = ...  # type: str
    fileobj = ...  # type: Optional[IO[bytes]]
    format = ...  # type: Optional[int]
    tarinfo = ...  # type: Optional[TarInfo]
    dereference = ...  # type: Optional[bool]
    ignore_zeros = ...  # type: Optional[bool]
    encoding = ...  # type: Optional[str]
    errors = ...  # type: str
    pax_headers = ...  # type: Optional[Mapping[str, str]]
    debug = ...  # type: Optional[int]
    errorlevel = ...  # type: Optional[int]
    if sys.version_info < (3,):
        posix = ...  # type: bool
    def __init__(self, name: Optional[str] = ..., mode: str = ...,
                 fileobj: Optional[IO[bytes]] = ...,
                 format: Optional[int] = ..., tarinfo: Optional[TarInfo] = ...,
                 dereference: Optional[bool] = ...,
                 ignore_zeros: Optional[bool] = ...,
                 encoding: Optional[str] = ..., errors: str = ...,
                 pax_headers: Optional[Mapping[str, str]] = ...,
                 debug: Optional[int] = ...,
                 errorlevel: Optional[int] = ...) -> None: ...
    def __enter__(self) -> TarFile: ...
    def __exit__(self,
                 exc_type: Optional[Type[BaseException]],
                 exc_val: Optional[Exception],
                 exc_tb: Optional[TracebackType]) -> bool: ...
    def __iter__(self) -> Iterator[TarInfo]: ...
    @classmethod
    def open(cls, name: Optional[str] = ..., mode: str = ...,
             fileobj: Optional[IO[bytes]] = ..., bufsize: int = ...,
             *, format: Optional[int] = ..., tarinfo: Optional[TarInfo] = ...,
             dereference: Optional[bool] = ...,
             ignore_zeros: Optional[bool] = ...,
             encoding: Optional[str] = ..., errors: str = ...,
             pax_headers: Optional[Mapping[str, str]] = ...,
             debug: Optional[int] = ...,
             errorlevel: Optional[int] = ...) -> TarFile: ...
    def getmember(self, name: str) -> TarInfo: ...
    def getmembers(self) -> List[TarInfo]: ...
    def getnames(self) -> List[str]: ...
    if sys.version_info >= (3, 5):
        def list(self, verbose: bool = ...,
                 *, members: Optional[List[TarInfo]] = ...) -> None: ...
    else:
        def list(self, verbose: bool = ...) -> None: ...
    def next(self) -> Optional[TarInfo]: ...
    if sys.version_info >= (3, 5):
        def extractall(self, path: str = ...,
                       members: Optional[List[TarInfo]] = ...,
                       *, numeric_owner: bool = ...) -> None: ...
    else:
        def extractall(self, path: str = ...,
                       members: Optional[List[TarInfo]] = ...) -> None: ...
    if sys.version_info >= (3, 5):
        def extract(self, member: Union[str, TarInfo], path: str = ...,
                    set_attrs: bool = ...,
                    *, numeric_owner: bool = ...) -> None: ...
    elif sys.version_info >= (3,):
        def extract(self, member: Union[str, TarInfo], path: str = ...,
                    set_attrs: bool = ...) -> None: ...
    else:
        def extract(self, member: Union[str, TarInfo],
                    path: str = ...) -> None: ...
    def extractfile(self,
                    member: Union[str, TarInfo]) -> Optional[IO[bytes]]: ...
    if sys.version_info >= (3,):
        def add(self, name: str, arcname: Optional[str] = ...,
                recursive: bool = ...,
                exclude: Optional[Callable[[str], bool]] = ..., *,
                filter: Optional[Callable[[TarInfo], Optional[TarInfo]]] = ...) -> None: ...
    else:
        def add(self, name: str, arcname: Optional[str] = ...,
                recursive: bool = ...,
                exclude: Optional[Callable[[str], bool]] = ...,
                filter: Optional[Callable[[TarInfo], Optional[TarInfo]]] = ...) -> None: ...
    def addfile(self, tarinfo: TarInfo,
                fileobj: Optional[IO[bytes]] = ...) -> None: ...
    def gettarinfo(self, name: Optional[str] = ...,
                   arcname: Optional[str] = ...,
                   fileobj: Optional[IO[bytes]] = ...) -> TarInfo: ...
    def close(self) -> None: ...


def is_tarfile(name: str) -> bool: ...


if sys.version_info < (3,):
    class TarFileCompat:
        def __init__(self, filename: str, mode: str = ...,
                     compression: int = ...) -> None: ...


class TarError(Exception): ...
class ReadError(TarError): ...
class CompressionError(TarError): ...
class StreamError(TarError): ...
class ExtractError(TarError): ...
class HeaderError(TarError): ...


class TarInfo:
    name = ...  # type: str
    size = ...  # type: int
    mtime = ...  # type: int
    mode = ...  # type: int
    type = ...  # type: bytes
    linkname = ...  # type: str
    uid = ...  # type: int
    gid = ...  # type: int
    uname = ...  # type: str
    gname = ...  # type: str
    pax_headers = ...  # type: Mapping[str, str]
    def __init__(self, name: str = ...) -> None: ...
    if sys.version_info >= (3,):
        @classmethod
        def frombuf(cls, buf: bytes, encoding: str, errors: str) -> TarInfo: ...
    else:
        @classmethod
        def frombuf(cls, buf: bytes) -> TarInfo: ...
    @classmethod
    def fromtarfile(cls, tarfile: TarFile) -> TarInfo: ...
    def tobuf(self, format: Optional[int] = ...,
              encoding: Optional[str] = ..., errors: str = ...) -> bytes: ...
    def isfile(self) -> bool: ...
    def isreg(self) -> bool: ...
    def isdir(self) -> bool: ...
    def issym(self) -> bool: ...
    def islnk(self) -> bool: ...
    def ischr(self) -> bool: ...
    def isblk(self) -> bool: ...
    def isfifo(self) -> bool: ...
    def isdev(self) -> bool: ...
