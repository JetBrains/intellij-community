import sys
from _typeshed import Incomplete
from collections.abc import Callable
from ctypes import _CData, c_ulong
from ctypes.wintypes import PFLOAT
from typing import TypeVar
from typing_extensions import TypeAlias

from d3dshot.capture_output import _Frame

_ProcessFuncRegionArg = TypeVar("_ProcessFuncRegionArg", tuple[int, int, int, int], None)
_ProcessFuncReturn = TypeVar("_ProcessFuncReturn", _Frame, None)
# The _ProcessFunc alias is used in multiple submodules
_ProcessFunc: TypeAlias = Callable[[PFLOAT, int, int, int, int, _ProcessFuncRegionArg, int], _ProcessFuncReturn]  # noqa: Y047

if sys.platform == "win32":
    from ctypes import HRESULT

    _HRESULT: TypeAlias = HRESULT
else:
    _HRESULT: TypeAlias = Incomplete

# TODO: Use comtypes.IUnknown once we can import non-types dependencies
# See: #5768
class _IUnknown(_CData):
    def QueryInterface(self, interface: type, iid: _CData | None = ...) -> _HRESULT: ...
    def AddRef(self) -> c_ulong: ...
    def Release(self) -> c_ulong: ...
