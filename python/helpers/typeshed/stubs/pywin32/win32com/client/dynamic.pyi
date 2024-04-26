from _typeshed import Incomplete
from typing import Any, Protocol, TypeVar, overload
from typing_extensions import TypeAlias

import _win32typing
from win32.lib.pywintypes import IIDType
from win32com.client import build as build

_T_co = TypeVar("_T_co", covariant=True)
_T = TypeVar("_T")

class _DispatchCreateClass(Protocol[_T_co]):
    @staticmethod
    def __call__(
        IDispatch: str | PyIDispatchType | _GoodDispatchTypes | PyIUnknownType,
        olerepr: build.DispatchItem | build.LazyDispatchItem,
        userName: str | None = ...,
        UnicodeToString: None = None,
        lazydata: Incomplete | None = ...,
    ) -> _T_co: ...

debugging: int
debugging_attr: int
LCID: int
ERRORS_BAD_CONTEXT: Incomplete
ALL_INVOKE_TYPES: Incomplete

def debug_print(*args) -> None: ...
def debug_attr_print(*args) -> None: ...
def MakeMethod(func, inst, cls): ...

PyIDispatchType = _win32typing.PyIDispatch
PyIUnknownType = _win32typing.PyIUnknown

_GoodDispatchTypes: TypeAlias = tuple[type[str], type[IIDType]]

@overload
def Dispatch(
    IDispatch: str | PyIDispatchType | _GoodDispatchTypes | PyIUnknownType,
    userName: str | None,
    createClass: _DispatchCreateClass[_T],
    typeinfo: _win32typing.PyITypeInfo | None = ...,
    UnicodeToString: None = None,
    clsctx: int = ...,
) -> _T: ...
@overload
def Dispatch(
    IDispatch: str | PyIDispatchType | _GoodDispatchTypes | PyIUnknownType,
    userName: str | None = ...,
    createClass: None = None,
    typeinfo: _win32typing.PyITypeInfo | None = ...,
    UnicodeToString: None = None,
    clsctx: int = ...,
) -> CDispatch: ...
def MakeOleRepr(IDispatch, typeinfo, typecomp): ...
def DumbDispatch(
    IDispatch,
    userName: Incomplete | None = ...,
    createClass: Incomplete | None = ...,
    UnicodeToString: Incomplete | None = ...,
    clsctx=...,
): ...

class CDispatch:
    def __init__(
        self,
        IDispatch,
        olerepr,
        userName: Incomplete | None = ...,
        UnicodeToString: None = None,
        lazydata: Incomplete | None = ...,
    ) -> None: ...
    def __call__(self, *args): ...
    def __bool__(self) -> bool: ...
    def __dir__(self): ...
    def __eq__(self, other): ...
    def __ne__(self, other): ...
    def __int__(self) -> int: ...
    def __len__(self) -> int: ...
    def __getitem__(self, index): ...
    def __setitem__(self, index, *args) -> None: ...
    def __LazyMap__(self, attr): ...
    def __AttrToID__(self, attr): ...
    ob: Incomplete
    # CDispatch objects are dynamically generated and too complex to type
    def __getattr__(self, attr: str) -> Any: ...
    def __setattr__(self, attr: str, value: Any) -> None: ...
