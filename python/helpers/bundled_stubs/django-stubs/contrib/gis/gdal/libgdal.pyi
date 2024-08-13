import sys
from _ctypes import _CData
from collections.abc import Callable, Sequence
from ctypes import CDLL, _FuncPointer, _NamedFuncPointer
from logging import Logger
from typing import Any

logger: Logger
lib_path: str | None
lib_names: list[str] | None
lib_name: str
lgdal: CDLL
if sys.platform == "win32":
    from ctypes import WinDLL

    lwingdal: WinDLL

def std_call(func: str) -> _NamedFuncPointer: ...
def gdal_version() -> bytes: ...
def gdal_full_version() -> bytes: ...
def gdal_version_info() -> tuple[int, int, int]: ...

GDAL_VERSION: tuple[int, int, int]
CPLErrorHandler: type[_FuncPointer]

def function(
    name: str,
    # Taken from _ctypes.CFuncPtr
    args: Sequence[type[_CData]],
    restype: type[_CData] | Callable[[int], Any] | None,
) -> _NamedFuncPointer: ...

err_handler: _FuncPointer
set_error_handler: _NamedFuncPointer
