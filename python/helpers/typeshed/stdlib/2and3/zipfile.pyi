# Stubs for zipfile

from typing import Callable, IO, Iterable, List, Optional, Text, Tuple, Type, Union
from types import TracebackType
import sys


_SZI = Union[Text, ZipInfo]
_DT = Tuple[int, int, int, int, int, int]


if sys.version_info >= (3,):
    class BadZipFile(Exception): ...
    BadZipfile = BadZipFile
else:
    class BadZipfile(Exception): ...
error = BadZipfile

class LargeZipFile(Exception): ...

class ZipFile:
    debug = ...  # type: int
    comment = ...  # type: bytes
    filelist = ...  # type: List[ZipInfo]
    def __init__(self, file: Union[Text, IO[bytes]], mode: Text = ..., compression: int = ...,
                 allowZip64: bool = ...) -> None: ...
    def __enter__(self) -> ZipFile: ...
    def __exit__(self, exc_type: Optional[Type[BaseException]],
                 exc_val: Optional[BaseException],
                 exc_tb: Optional[TracebackType]) -> bool: ...
    def close(self) -> None: ...
    def getinfo(self, name: Text) -> ZipInfo: ...
    def infolist(self) -> List[ZipInfo]: ...
    def namelist(self) -> List[Text]: ...
    def open(self, name: _SZI, mode: Text = ...,
             pwd: Optional[bytes] = ...) -> IO[bytes]: ...
    def extract(self, member: _SZI, path: Optional[_SZI] = ...,
                pwd: bytes = ...) -> str: ...
    def extractall(self, path: Optional[Text] = ...,
                   members: Optional[Iterable[Text]] = ...,
                   pwd: Optional[bytes] = ...) -> None: ...
    def printdir(self) -> None: ...
    def setpassword(self, pwd: bytes) -> None: ...
    def read(self, name: _SZI, pwd: Optional[bytes] = ...) -> bytes: ...
    def testzip(self) -> Optional[str]: ...
    def write(self, filename: Text, arcname: Optional[Text] = ...,
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
                    pathname: Text, basename: Text = ...) -> None: ...

class ZipInfo:
    filename = ...  # type: Text
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
        def __init__(self, filename: Optional[Text] = ...,
                     date_time: Optional[_DT] = ...) -> None: ...


def is_zipfile(filename: Union[Text, IO[bytes]]) -> bool: ...

ZIP_STORED = ...  # type: int
ZIP_DEFLATED = ...  # type: int
if sys.version_info >= (3, 3):
    ZIP_BZIP2 = ...  # type: int
    ZIP_LZMA = ...  # type: int
