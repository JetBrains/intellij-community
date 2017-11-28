# Stubs for types
# Note, all classes "defined" here require special handling.

# TODO parts of this should be conditional on version

import sys
from typing import (
    Any, Awaitable, Callable, Dict, Generic, Iterator, Mapping, Optional, Tuple, TypeVar,
    Union, overload, Type
)

# ModuleType is exported from this module, but for circular import
# reasons exists in its own stub file (with ModuleSpec and Loader).
from _importlib_modulespec import ModuleType as ModuleType  # Exported

_T = TypeVar('_T')
_T_co = TypeVar('_T_co', covariant=True)
_T_contra = TypeVar('_T_contra', contravariant=True)
_KT = TypeVar('_KT')
_VT = TypeVar('_VT')

class _Cell:
    cell_contents = ...  # type: Any

class FunctionType:
    __closure__ = ...  # type: Optional[Tuple[_Cell, ...]]
    __code__ = ...  # type: CodeType
    __defaults__ = ...  # type: Optional[Tuple[Any, ...]]
    __dict__ = ...  # type: Dict[str, Any]
    __globals__ = ...  # type: Dict[str, Any]
    __name__ = ...  # type: str
    __annotations__ = ...  # type: Dict[str, Any]
    __kwdefaults__ = ...  # type: Dict[str, Any]
    def __call__(self, *args: Any, **kwargs: Any) -> Any: ...
    def __get__(self, obj: Optional[object], type: Optional[type]) -> 'MethodType': ...
LambdaType = FunctionType

class CodeType:
    """Create a code object.  Not for the faint of heart."""
    co_argcount = ...  # type: int
    co_kwonlyargcount = ...  # type: int
    co_nlocals = ...  # type: int
    co_stacksize = ...  # type: int
    co_flags = ...  # type: int
    co_code = ...  # type: bytes
    co_consts = ...  # type: Tuple[Any, ...]
    co_names = ...  # type: Tuple[str, ...]
    co_varnames = ...  # type: Tuple[str, ...]
    co_filename = ...  # type: Optional[str]
    co_name = ...  # type: str
    co_firstlineno = ...  # type: int
    co_lnotab = ...  # type: bytes
    co_freevars = ...  # type: Tuple[str, ...]
    co_cellvars = ...  # type: Tuple[str, ...]
    def __init__(
        self,
        argcount: int,
        kwonlyargcount: int,
        nlocals: int,
        stacksize: int,
        flags: int,
        codestring: bytes,
        constants: Tuple[Any, ...],
        names: Tuple[str, ...],
        varnames: Tuple[str, ...],
        filename: str,
        name: str,
        firstlineno: int,
        lnotab: bytes,
        freevars: Tuple[str, ...] = ...,
        cellvars: Tuple[str, ...] = ...,
    ) -> None: ...

class MappingProxyType(Mapping[_KT, _VT], Generic[_KT, _VT]):
    def __init__(self, mapping: Mapping[_KT, _VT]) -> None: ...
    def __getitem__(self, k: _KT) -> _VT: ...
    def __iter__(self) -> Iterator[_KT]: ...
    def __len__(self) -> int: ...

# TODO: use __getattr__ and __setattr__ instead of inheriting from Any, pending mypy#521.
class SimpleNamespace(Any): ...  # type: ignore

class GeneratorType:
    gi_code = ...  # type: CodeType
    gi_frame = ...  # type: FrameType
    gi_running = ...  # type: bool
    gi_yieldfrom = ...  # type: Optional[GeneratorType]
    def __iter__(self) -> 'GeneratorType': ...
    def __next__(self) -> Any: ...
    def close(self) -> None: ...
    def send(self, arg: Any) -> Any: ...
    @overload
    def throw(self, val: BaseException) -> Any: ...
    @overload
    def throw(self, typ: type, val: BaseException = ..., tb: 'TracebackType' = ...) -> Any: ...

if sys.version_info >= (3, 6):
    class AsyncGeneratorType(Generic[_T_co, _T_contra]):
        ag_await: Optional[Awaitable[Any]]
        ag_frame: FrameType
        ag_running: bool
        ag_code: CodeType
        def __aiter__(self) -> Awaitable[AsyncGeneratorType[_T_co, _T_contra]]: ...
        def __anext__(self) -> Awaitable[_T_co]: ...
        def asend(self, val: _T_contra) -> Awaitable[_T_co]: ...
        @overload
        def athrow(self, val: BaseException) -> Awaitable[_T_co]: ...
        @overload
        def athrow(self, typ: Type[BaseException], val: BaseException, tb: TracebackType = ...) -> Awaitable[_T_co]: ...
        def aclose(self) -> Awaitable[_T_co]: ...

class CoroutineType:
    cr_await = ...  # type: Optional[Any]
    cr_code = ...  # type: CodeType
    cr_frame = ...  # type: FrameType
    cr_running = ...  # type: bool
    def close(self) -> None: ...
    def send(self, arg: Any) -> Any: ...
    @overload
    def throw(self, val: BaseException) -> Any: ...
    @overload
    def throw(self, typ: type, val: BaseException = ..., tb: 'TracebackType' = ...) -> Any: ...

class _StaticFunctionType:
    """Fictional type to correct the type of MethodType.__func__.

    FunctionType is a descriptor, so mypy follows the descriptor protocol and
    converts MethodType.__func__ back to MethodType (the return type of
    FunctionType.__get__). But this is actually a special case; MethodType is
    implemented in C and its attribute access doesn't go through
    __getattribute__.

    By wrapping FunctionType in _StaticFunctionType, we get the right result;
    similar to wrapping a function in staticmethod() at runtime to prevent it
    being bound as a method.
    """
    def __get__(self, obj: Optional[object], type: Optional[type]) -> 'FunctionType': ...

class MethodType:
    __func__ = ...  # type: _StaticFunctionType
    __self__ = ...  # type: object
    __name__ = ...  # type: str
    def __init__(self, func: Callable, obj: object) -> None: ...
    def __call__(self, *args: Any, **kwargs: Any) -> Any: ...
class BuiltinFunctionType:
    __self__ = ...  # type: Union[object, ModuleType]
    def __call__(self, *args: Any, **kwargs: Any) -> Any: ...
BuiltinMethodType = BuiltinFunctionType

class TracebackType:
    tb_frame = ...  # type: FrameType
    tb_lasti = ...  # type: int
    tb_lineno = ...  # type: int
    tb_next = ...  # type: TracebackType

class FrameType:
    f_back = ...  # type: FrameType
    f_builtins = ...  # type: Dict[str, Any]
    f_code = ...  # type: CodeType
    f_globals = ...  # type: Dict[str, Any]
    f_lasti = ...  # type: int
    f_lineno = ...  # type: int
    f_locals = ...  # type: Dict[str, Any]
    f_trace = ...  # type: Callable[[], None]

    def clear(self) -> None: ...

class GetSetDescriptorType:
    __name__ = ...  # type: str
    __objclass__ = ...  # type: type
    def __get__(self, obj: Any, type: type = ...) -> Any: ...
    def __set__(self, obj: Any) -> None: ...
    def __delete__(self, obj: Any) -> None: ...
class MemberDescriptorType:
    __name__ = ...  # type: str
    __objclass__ = ...  # type: type
    def __get__(self, obj: Any, type: type = ...) -> Any: ...
    def __set__(self, obj: Any) -> None: ...
    def __delete__(self, obj: Any) -> None: ...

def new_class(name: str, bases: Tuple[type, ...] = ..., kwds: Dict[str, Any] = ..., exec_body: Callable[[Dict[str, Any]], None] = ...) -> type: ...
def prepare_class(name: str, bases: Tuple[type, ...] = ..., kwds: Dict[str, Any] = ...) -> Tuple[type, Dict[str, Any], Dict[str, Any]]: ...

# Actually a different type, but `property` is special and we want that too.
DynamicClassAttribute = property

def coroutine(f: Callable[..., Any]) -> CoroutineType: ...
