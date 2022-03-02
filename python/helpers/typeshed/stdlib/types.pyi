import sys
from _typeshed import SupportsKeysAndGetItem
from importlib.abc import _LoaderProtocol
from importlib.machinery import ModuleSpec
from typing import (
    Any,
    AsyncGenerator,
    Awaitable,
    Callable,
    Coroutine,
    Generator,
    Generic,
    ItemsView,
    Iterable,
    Iterator,
    KeysView,
    Mapping,
    MutableSequence,
    TypeVar,
    ValuesView,
    overload,
)
from typing_extensions import Literal, ParamSpec, final

# Note, all classes "defined" here require special handling.

_T1 = TypeVar("_T1")
_T2 = TypeVar("_T2")
_T_co = TypeVar("_T_co", covariant=True)
_T_contra = TypeVar("_T_contra", contravariant=True)
_KT = TypeVar("_KT")
_VT_co = TypeVar("_VT_co", covariant=True)
_V_co = TypeVar("_V_co", covariant=True)

@final
class _Cell:
    __hash__: None  # type: ignore[assignment]
    cell_contents: Any

# Make sure this class definition stays roughly in line with `builtins.function`
@final
class FunctionType:
    __closure__: tuple[_Cell, ...] | None
    __code__: CodeType
    __defaults__: tuple[Any, ...] | None
    __dict__: dict[str, Any]
    __globals__: dict[str, Any]
    __name__: str
    __qualname__: str
    __annotations__: dict[str, Any]
    __kwdefaults__: dict[str, Any]
    def __init__(
        self,
        code: CodeType,
        globals: dict[str, Any],
        name: str | None = ...,
        argdefs: tuple[object, ...] | None = ...,
        closure: tuple[_Cell, ...] | None = ...,
    ) -> None: ...
    def __call__(self, *args: Any, **kwargs: Any) -> Any: ...
    @overload
    def __get__(self, obj: None, type: type) -> FunctionType: ...
    @overload
    def __get__(self, obj: object, type: type | None = ...) -> MethodType: ...

LambdaType = FunctionType

@final
class CodeType:
    co_argcount: int
    if sys.version_info >= (3, 8):
        co_posonlyargcount: int
    co_kwonlyargcount: int
    co_nlocals: int
    co_stacksize: int
    co_flags: int
    co_code: bytes
    co_consts: tuple[Any, ...]
    co_names: tuple[str, ...]
    co_varnames: tuple[str, ...]
    co_filename: str
    co_name: str
    co_firstlineno: int
    co_lnotab: bytes
    co_freevars: tuple[str, ...]
    co_cellvars: tuple[str, ...]
    if sys.version_info >= (3, 8):
        def __init__(
            self,
            argcount: int,
            posonlyargcount: int,
            kwonlyargcount: int,
            nlocals: int,
            stacksize: int,
            flags: int,
            codestring: bytes,
            constants: tuple[Any, ...],
            names: tuple[str, ...],
            varnames: tuple[str, ...],
            filename: str,
            name: str,
            firstlineno: int,
            lnotab: bytes,
            freevars: tuple[str, ...] = ...,
            cellvars: tuple[str, ...] = ...,
        ) -> None: ...
    else:
        def __init__(
            self,
            argcount: int,
            kwonlyargcount: int,
            nlocals: int,
            stacksize: int,
            flags: int,
            codestring: bytes,
            constants: tuple[Any, ...],
            names: tuple[str, ...],
            varnames: tuple[str, ...],
            filename: str,
            name: str,
            firstlineno: int,
            lnotab: bytes,
            freevars: tuple[str, ...] = ...,
            cellvars: tuple[str, ...] = ...,
        ) -> None: ...
    if sys.version_info >= (3, 10):
        def replace(
            self,
            *,
            co_argcount: int = ...,
            co_posonlyargcount: int = ...,
            co_kwonlyargcount: int = ...,
            co_nlocals: int = ...,
            co_stacksize: int = ...,
            co_flags: int = ...,
            co_firstlineno: int = ...,
            co_code: bytes = ...,
            co_consts: tuple[Any, ...] = ...,
            co_names: tuple[str, ...] = ...,
            co_varnames: tuple[str, ...] = ...,
            co_freevars: tuple[str, ...] = ...,
            co_cellvars: tuple[str, ...] = ...,
            co_filename: str = ...,
            co_name: str = ...,
            co_linetable: object = ...,
        ) -> CodeType: ...
        def co_lines(self) -> Iterator[tuple[int, int, int | None]]: ...
        co_linetable: object
    elif sys.version_info >= (3, 8):
        def replace(
            self,
            *,
            co_argcount: int = ...,
            co_posonlyargcount: int = ...,
            co_kwonlyargcount: int = ...,
            co_nlocals: int = ...,
            co_stacksize: int = ...,
            co_flags: int = ...,
            co_firstlineno: int = ...,
            co_code: bytes = ...,
            co_consts: tuple[Any, ...] = ...,
            co_names: tuple[str, ...] = ...,
            co_varnames: tuple[str, ...] = ...,
            co_freevars: tuple[str, ...] = ...,
            co_cellvars: tuple[str, ...] = ...,
            co_filename: str = ...,
            co_name: str = ...,
            co_lnotab: bytes = ...,
        ) -> CodeType: ...
    if sys.version_info >= (3, 11):
        def co_positions(self) -> Iterable[tuple[int | None, int | None, int | None, int | None]]: ...

@final
class MappingProxyType(Mapping[_KT, _VT_co], Generic[_KT, _VT_co]):
    __hash__: None  # type: ignore[assignment]
    def __init__(self, mapping: SupportsKeysAndGetItem[_KT, _VT_co]) -> None: ...
    def __getitem__(self, k: _KT) -> _VT_co: ...
    def __iter__(self) -> Iterator[_KT]: ...
    def __len__(self) -> int: ...
    def copy(self) -> dict[_KT, _VT_co]: ...
    def keys(self) -> KeysView[_KT]: ...
    def values(self) -> ValuesView[_VT_co]: ...
    def items(self) -> ItemsView[_KT, _VT_co]: ...
    if sys.version_info >= (3, 9):
        def __class_getitem__(cls, item: Any) -> GenericAlias: ...
        def __reversed__(self) -> Iterator[_KT]: ...
        def __or__(self, __value: Mapping[_T1, _T2]) -> dict[_KT | _T1, _VT_co | _T2]: ...
        def __ror__(self, __value: Mapping[_T1, _T2]) -> dict[_KT | _T1, _VT_co | _T2]: ...

class SimpleNamespace:
    __hash__: None  # type: ignore[assignment]
    def __init__(self, **kwargs: Any) -> None: ...
    def __getattribute__(self, name: str) -> Any: ...
    def __setattr__(self, name: str, value: Any) -> None: ...
    def __delattr__(self, name: str) -> None: ...

class ModuleType:
    __name__: str
    __file__: str | None
    __dict__: dict[str, Any]
    __loader__: _LoaderProtocol | None
    __package__: str | None
    __path__: MutableSequence[str]
    __spec__: ModuleSpec | None
    def __init__(self, name: str, doc: str | None = ...) -> None: ...
    # __getattr__ doesn't exist at runtime,
    # but having it here in typeshed makes dynamic imports
    # using `builtins.__import__` or `importlib.import_module` less painful
    def __getattr__(self, name: str) -> Any: ...

@final
class GeneratorType(Generator[_T_co, _T_contra, _V_co]):
    gi_code: CodeType
    gi_frame: FrameType
    gi_running: bool
    gi_yieldfrom: GeneratorType[_T_co, _T_contra, Any] | None
    def __iter__(self) -> GeneratorType[_T_co, _T_contra, _V_co]: ...
    def __next__(self) -> _T_co: ...
    def close(self) -> None: ...
    def send(self, __arg: _T_contra) -> _T_co: ...
    @overload
    def throw(
        self, __typ: type[BaseException], __val: BaseException | object = ..., __tb: TracebackType | None = ...
    ) -> _T_co: ...
    @overload
    def throw(self, __typ: BaseException, __val: None = ..., __tb: TracebackType | None = ...) -> _T_co: ...

@final
class AsyncGeneratorType(AsyncGenerator[_T_co, _T_contra]):
    ag_await: Awaitable[Any] | None
    ag_frame: FrameType
    ag_running: bool
    ag_code: CodeType
    def __aiter__(self) -> AsyncGeneratorType[_T_co, _T_contra]: ...
    async def __anext__(self) -> _T_co: ...
    async def asend(self, __val: _T_contra) -> _T_co: ...
    @overload
    async def athrow(
        self, __typ: type[BaseException], __val: BaseException | object = ..., __tb: TracebackType | None = ...
    ) -> _T_co: ...
    @overload
    async def athrow(self, __typ: BaseException, __val: None = ..., __tb: TracebackType | None = ...) -> _T_co: ...
    async def aclose(self) -> None: ...
    if sys.version_info >= (3, 9):
        def __class_getitem__(cls, __item: Any) -> GenericAlias: ...

@final
class CoroutineType(Coroutine[_T_co, _T_contra, _V_co]):
    __name__: str
    __qualname__: str
    cr_await: Any | None
    cr_code: CodeType
    cr_frame: FrameType
    cr_running: bool
    def close(self) -> None: ...
    def __await__(self) -> Generator[Any, None, _V_co]: ...
    def send(self, __arg: _T_contra) -> _T_co: ...
    @overload
    def throw(
        self, __typ: type[BaseException], __val: BaseException | object = ..., __tb: TracebackType | None = ...
    ) -> _T_co: ...
    @overload
    def throw(self, __typ: BaseException, __val: None = ..., __tb: TracebackType | None = ...) -> _T_co: ...

class _StaticFunctionType:
    # Fictional type to correct the type of MethodType.__func__.
    # FunctionType is a descriptor, so mypy follows the descriptor protocol and
    # converts MethodType.__func__ back to MethodType (the return type of
    # FunctionType.__get__). But this is actually a special case; MethodType is
    # implemented in C and its attribute access doesn't go through
    # __getattribute__.
    # By wrapping FunctionType in _StaticFunctionType, we get the right result;
    # similar to wrapping a function in staticmethod() at runtime to prevent it
    # being bound as a method.
    def __get__(self, obj: object | None, type: type | None) -> FunctionType: ...

@final
class MethodType:
    __closure__: tuple[_Cell, ...] | None  # inherited from the added function
    __defaults__: tuple[Any, ...] | None  # inherited from the added function
    __func__: _StaticFunctionType
    __self__: object
    __name__: str  # inherited from the added function
    __qualname__: str  # inherited from the added function
    def __init__(self, func: Callable[..., Any], obj: object) -> None: ...
    def __call__(self, *args: Any, **kwargs: Any) -> Any: ...

@final
class BuiltinFunctionType:
    __self__: object | ModuleType
    __name__: str
    __qualname__: str
    def __call__(self, *args: Any, **kwargs: Any) -> Any: ...

BuiltinMethodType = BuiltinFunctionType

if sys.version_info >= (3, 7):
    @final
    class WrapperDescriptorType:
        __name__: str
        __qualname__: str
        __objclass__: type
        def __call__(self, *args: Any, **kwargs: Any) -> Any: ...
        def __get__(self, obj: Any, type: type = ...) -> Any: ...

    @final
    class MethodWrapperType:
        __self__: object
        __name__: str
        __qualname__: str
        __objclass__: type
        def __call__(self, *args: Any, **kwargs: Any) -> Any: ...
        def __eq__(self, other: object) -> bool: ...
        def __ne__(self, other: object) -> bool: ...

    @final
    class MethodDescriptorType:
        __name__: str
        __qualname__: str
        __objclass__: type
        def __call__(self, *args: Any, **kwargs: Any) -> Any: ...
        def __get__(self, obj: Any, type: type = ...) -> Any: ...

    @final
    class ClassMethodDescriptorType:
        __name__: str
        __qualname__: str
        __objclass__: type
        def __call__(self, *args: Any, **kwargs: Any) -> Any: ...
        def __get__(self, obj: Any, type: type = ...) -> Any: ...

@final
class TracebackType:
    if sys.version_info >= (3, 7):
        def __init__(self, tb_next: TracebackType | None, tb_frame: FrameType, tb_lasti: int, tb_lineno: int) -> None: ...
        tb_next: TracebackType | None
    else:
        @property
        def tb_next(self) -> TracebackType | None: ...
    # the rest are read-only even in 3.7
    @property
    def tb_frame(self) -> FrameType: ...
    @property
    def tb_lasti(self) -> int: ...
    @property
    def tb_lineno(self) -> int: ...

@final
class FrameType:
    f_back: FrameType | None
    f_builtins: dict[str, Any]
    f_code: CodeType
    f_globals: dict[str, Any]
    f_lasti: int
    # see discussion in #6769: f_lineno *can* sometimes be None,
    # but you should probably file a bug report with CPython if you encounter it being None in the wild.
    # An `int | None` annotation here causes too many false-positive errors.
    f_lineno: int | Any
    f_locals: dict[str, Any]
    f_trace: Callable[[FrameType, str, Any], Any] | None
    if sys.version_info >= (3, 7):
        f_trace_lines: bool
        f_trace_opcodes: bool
    def clear(self) -> None: ...

@final
class GetSetDescriptorType:
    __name__: str
    __objclass__: type
    def __get__(self, __obj: Any, __type: type = ...) -> Any: ...
    def __set__(self, __instance: Any, __value: Any) -> None: ...
    def __delete__(self, obj: Any) -> None: ...

@final
class MemberDescriptorType:
    __name__: str
    __objclass__: type
    def __get__(self, __obj: Any, __type: type = ...) -> Any: ...
    def __set__(self, __instance: Any, __value: Any) -> None: ...
    def __delete__(self, obj: Any) -> None: ...

if sys.version_info >= (3, 7):
    def new_class(
        name: str,
        bases: Iterable[object] = ...,
        kwds: dict[str, Any] | None = ...,
        exec_body: Callable[[dict[str, Any]], None] | None = ...,
    ) -> type: ...
    def resolve_bases(bases: Iterable[object]) -> tuple[Any, ...]: ...

else:
    def new_class(
        name: str,
        bases: tuple[type, ...] = ...,
        kwds: dict[str, Any] | None = ...,
        exec_body: Callable[[dict[str, Any]], None] | None = ...,
    ) -> type: ...

def prepare_class(
    name: str, bases: tuple[type, ...] = ..., kwds: dict[str, Any] | None = ...
) -> tuple[type, dict[str, Any], dict[str, Any]]: ...

# Actually a different type, but `property` is special and we want that too.
DynamicClassAttribute = property

_Fn = TypeVar("_Fn", bound=Callable[..., object])
_R = TypeVar("_R")
_P = ParamSpec("_P")

# it's not really an Awaitable, but can be used in an await expression. Real type: Generator & Awaitable
# The type: ignore is due to overlapping overloads, not the use of ParamSpec
@overload
def coroutine(func: Callable[_P, Generator[_R, Any, Any]]) -> Callable[_P, Awaitable[_R]]: ...  # type: ignore[misc]
@overload
def coroutine(func: _Fn) -> _Fn: ...

if sys.version_info >= (3, 8):
    CellType = _Cell

if sys.version_info >= (3, 9):
    class GenericAlias:
        __origin__: type
        __args__: tuple[Any, ...]
        __parameters__: tuple[Any, ...]
        def __init__(self, origin: type, args: Any) -> None: ...
        def __getattr__(self, name: str) -> Any: ...  # incomplete

if sys.version_info >= (3, 10):
    @final
    class NoneType:
        def __bool__(self) -> Literal[False]: ...
    EllipsisType = ellipsis  # noqa F811 from builtins
    from builtins import _NotImplementedType

    NotImplementedType = _NotImplementedType  # noqa F811 from builtins
    @final
    class UnionType:
        __args__: tuple[Any, ...]
        def __or__(self, obj: Any) -> UnionType: ...
        def __ror__(self, obj: Any) -> UnionType: ...
