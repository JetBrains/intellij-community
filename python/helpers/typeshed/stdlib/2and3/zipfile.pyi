# Stubs for zipfile

from typing import Callable, IO, List, Optional, Tuple, Type, Union
from types import TracebackType
import sys


_SZI = Union[str, ZipInfo]
_DT = Tuple[int, int, int, int, int, int]


if sys.version_info >= (3,):
    class BadZipFile(Exception): ...
    BadZipfile = BadZipFile
else:
    class BadZipfile(Exception): ...

class LargeZipFile(Exception): ...

class ZipFile:
    debug = ...  # type: int
    comment = ...  # type: bytes
    def __init__(self, file: Union[str, IO[bytes]], mode: str = ..., compression: int = ...,
                 allowZip64: bool = ...) -> None: ...
    def __enter__(self) -> ZipFile: ...
    def __exit__(self, exc_type: Optional[Type[BaseException]],
                 exc_val: Optional[Exception],
                 exc_tb: Optional[TracebackType]) -> bool: ...
    def close(self) -> None: ...
    def getinfo(self, name: str) -> ZipInfo: ...
    def infolist(self) -> List[ZipInfo]: ...
    def namelist(self) -> List[str]: ...
    def open(self, name: _SZI, mode: str = ...,
             pwd: Optional[bytes] = ...) -> IO[bytes]: ...
    def extract(self, member: _SZI, path: Optional[_SZI] = ...,
                pwd: bytes = ...) -> str: ...
    def extractall(self, path: Optional[str] = ...,
                   members: Optional[List[str]] = ...,
                   pwd: Optional[bytes] = ...) -> None: ...
    def printdir(self) -> None: ...
    def setpassword(self, pwd: bytes) -> None: ...
    def read(self, name: _SZI, pwd: Optional[bytes] = ...) -> bytes: ...
    def testzip(self) -> Optional[str]: ...
    def write(self, filename: str, arcname: Optional[str] = ...,
              compress_type: Optional[int] = ...) -> None: ...
    if sys.version_info >= (3,):
        def writestr(self, zinfo_or_arcname: _SZI, data: Union[bytes, str],
                     compress_type: Optional[int] = ...) -> None: ...
    else:
        def writestr(self,
                     zinfo_or_arcname: _SZI, bytes: bytes,
                     compress_type: Optional[int] = ...) -> None: ...

class PyZipFile(ZipFile):
    if sys.version_info >= (3,):
        def __init__(self, file: Union[str, IO[bytes]], mode: str = ...,
                     compression: int = ..., allowZip64: bool = ...,
                     opimize: int = ...) -> None: ...
        def writepy(self, pathname: str, basename: str = ...,
                    filterfunc: Optional[Callable[[str], bool]] = ...) -> None: ...
    else:
        def writepy(self,
                    pathname: str, basename: str = ...) -> None: ...

class ZipInfo:
    filename = ...  # type: str
    date_time = ...  # type: _DT
    compress_type = ...  # type: int
    comment = ...  # type: bytes
    extra = ...  # type: bytes
    create_system = ...  # type: int
    create_version = ...  # type: int
    extract_version = ...  # type: int
    reserved = ...  # type: int
    flag_bits = ...  # type: int
    volume = ...  # type: int
    internal_attr = ...  # type: int
    external_attr = ...  # type: int
    header_offset = ...  # type: int
    CRC = ...  # type: int
    compress_size = ...  # type: int
    file_size = ...  # type: int
    if sys.version_info < (3,):
        def __init__(self, filename: Optional[str] = ...,
                     date_time: Optional[_DT] = ...) -> None: ...


def is_zipfile(filename: Union[str, IO[bytes]]) -> bool: ...

ZIP_STORED = ...  # type: int
ZIP_DEFLATED = ...  # type: int
if sys.version_info >= (3, 3):
    ZIP_BZIP2 = ...  # type: int
    ZIP_LZMA = ...  # type: int
