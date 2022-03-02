import enum
import sys
import types
from _typeshed import Self
from collections import OrderedDict
from collections.abc import Awaitable, Callable, Generator, Mapping, Sequence, Set as AbstractSet
from types import (
    AsyncGeneratorType,
    BuiltinFunctionType,
    BuiltinMethodType,
    CodeType,
    CoroutineType,
    FrameType,
    FunctionType,
    GeneratorType,
    GetSetDescriptorType,
    LambdaType,
    MethodType,
    ModuleType,
    TracebackType,
)

if sys.version_info >= (3, 7):
    from types import ClassMethodDescriptorType, WrapperDescriptorType, MemberDescriptorType, MethodDescriptorType

from typing import Any, ClassVar, Coroutine, NamedTuple, Protocol, TypeVar, Union
from typing_extensions import Literal, ParamSpec, TypeGuard

_P = ParamSpec("_P")
_T_cont = TypeVar("_T_cont", contravariant=True)
_V_cont = TypeVar("_V_cont", contravariant=True)

#
# Types and members
#
class EndOfBlock(Exception): ...

class BlockFinder:
    indent: int
    islambda: bool
    started: bool
    passline: bool
    indecorator: bool
    decoratorhasargs: bool
    last: int
    def tokeneater(self, type: int, token: str, srowcol: tuple[int, int], erowcol: tuple[int, int], line: str) -> None: ...

CO_OPTIMIZED: Literal[1]
CO_NEWLOCALS: Literal[2]
CO_VARARGS: Literal[4]
CO_VARKEYWORDS: Literal[8]
CO_NESTED: Literal[16]
CO_GENERATOR: Literal[32]
CO_NOFREE: Literal[64]
CO_COROUTINE: Literal[128]
CO_ITERABLE_COROUTINE: Literal[256]
CO_ASYNC_GENERATOR: Literal[512]
TPFLAGS_IS_ABSTRACT: Literal[1048576]

modulesbyfile: dict[str, Any]

def getmembers(object: object, predicate: Callable[[Any], bool] | None = ...) -> list[tuple[str, Any]]: ...
def getmodulename(path: str) -> str | None: ...
def ismodule(object: object) -> TypeGuard[ModuleType]: ...
def isclass(object: object) -> TypeGuard[type[Any]]: ...
def ismethod(object: object) -> TypeGuard[MethodType]: ...
def isfunction(object: object) -> TypeGuard[FunctionType]: ...

if sys.version_info >= (3, 8):
    def isgeneratorfunction(obj: object) -> bool: ...
    def iscoroutinefunction(obj: object) -> bool: ...

else:
    def isgeneratorfunction(object: object) -> bool: ...
    def iscoroutinefunction(object: object) -> bool: ...

def isgenerator(object: object) -> TypeGuard[GeneratorType[Any, Any, Any]]: ...
def iscoroutine(object: object) -> TypeGuard[CoroutineType[Any, Any, Any]]: ...
def isawaitable(object: object) -> TypeGuard[Awaitable[Any]]: ...

if sys.version_info >= (3, 8):
    def isasyncgenfunction(obj: object) -> bool: ...

else:
    def isasyncgenfunction(object: object) -> bool: ...

class _SupportsSet(Protocol[_T_cont, _V_cont]):
    def __set__(self, __instance: _T_cont, __value: _V_cont) -> None: ...

class _SupportsDelete(Protocol[_T_cont]):
    def __delete__(self, __instance: _T_cont) -> None: ...

def isasyncgen(object: object) -> TypeGuard[AsyncGeneratorType[Any, Any]]: ...
def istraceback(object: object) -> TypeGuard[TracebackType]: ...
def isframe(object: object) -> TypeGuard[FrameType]: ...
def iscode(object: object) -> TypeGuard[CodeType]: ...
def isbuiltin(object: object) -> TypeGuard[BuiltinFunctionType]: ...

if sys.version_info >= (3, 7):
    def isroutine(
        object: object,
    ) -> TypeGuard[
        FunctionType
        | LambdaType
        | MethodType
        | BuiltinFunctionType
        | BuiltinMethodType
        | WrapperDescriptorType
        | MethodDescriptorType
        | ClassMethodDescriptorType
    ]: ...
    def ismethoddescriptor(object: object) -> TypeGuard[MethodDescriptorType]: ...
    def ismemberdescriptor(object: object) -> TypeGuard[MemberDescriptorType]: ...

else:
    def isroutine(
        object: object,
    ) -> TypeGuard[FunctionType | LambdaType | MethodType | BuiltinFunctionType | BuiltinMethodType]: ...
    def ismethoddescriptor(object: object) -> bool: ...
    def ismemberdescriptor(object: object) -> bool: ...

def isabstract(object: object) -> bool: ...
def isgetsetdescriptor(object: object) -> TypeGuard[GetSetDescriptorType]: ...
def isdatadescriptor(object: object) -> TypeGuard[_SupportsSet[Any, Any] | _SupportsDelete[Any]]: ...

#
# Retrieving source code
#
_SourceObjectType = Union[ModuleType, type[Any], MethodType, FunctionType, TracebackType, FrameType, CodeType, Callable[..., Any]]

def findsource(object: _SourceObjectType) -> tuple[list[str], int]: ...
def getabsfile(object: _SourceObjectType, _filename: str | None = ...) -> str: ...
def getblock(lines: Sequence[str]) -> Sequence[str]: ...
def getdoc(object: object) -> str | None: ...
def getcomments(object: object) -> str | None: ...
def getfile(object: _SourceObjectType) -> str: ...
def getmodule(object: object, _filename: str | None = ...) -> ModuleType | None: ...
def getsourcefile(object: _SourceObjectType) -> str | None: ...
def getsourcelines(object: _SourceObjectType) -> tuple[list[str], int]: ...
def getsource(object: _SourceObjectType) -> str: ...
def cleandoc(doc: str) -> str: ...
def indentsize(line: str) -> int: ...

#
# Introspecting callables with the Signature object
#
if sys.version_info >= (3, 10):
    def signature(
        obj: Callable[..., Any],
        *,
        follow_wrapped: bool = ...,
        globals: Mapping[str, Any] | None = ...,
        locals: Mapping[str, Any] | None = ...,
        eval_str: bool = ...,
    ) -> Signature: ...

else:
    def signature(obj: Callable[..., Any], *, follow_wrapped: bool = ...) -> Signature: ...

class _void: ...
class _empty: ...

class Signature:
    def __init__(
        self, parameters: Sequence[Parameter] | None = ..., *, return_annotation: Any = ..., __validate_parameters__: bool = ...
    ) -> None: ...
    empty = _empty
    @property
    def parameters(self) -> types.MappingProxyType[str, Parameter]: ...
    @property
    def return_annotation(self) -> Any: ...
    def bind(self, *args: Any, **kwargs: Any) -> BoundArguments: ...
    def bind_partial(self, *args: Any, **kwargs: Any) -> BoundArguments: ...
    def replace(
        self: Self, *, parameters: Sequence[Parameter] | type[_void] | None = ..., return_annotation: Any = ...
    ) -> Self: ...
    if sys.version_info >= (3, 10):
        @classmethod
        def from_callable(
            cls: type[Self],
            obj: Callable[..., Any],
            *,
            follow_wrapped: bool = ...,
            globals: Mapping[str, Any] | None = ...,
            locals: Mapping[str, Any] | None = ...,
            eval_str: bool = ...,
        ) -> Self: ...
    else:
        @classmethod
        def from_callable(cls: type[Self], obj: Callable[..., Any], *, follow_wrapped: bool = ...) -> Self: ...

if sys.version_info >= (3, 10):
    def get_annotations(
        obj: Callable[..., Any] | type[Any] | ModuleType,
        *,
        globals: Mapping[str, Any] | None = ...,
        locals: Mapping[str, Any] | None = ...,
        eval_str: bool = ...,
    ) -> dict[str, Any]: ...

# The name is the same as the enum's name in CPython
class _ParameterKind(enum.IntEnum):
    POSITIONAL_ONLY: int
    POSITIONAL_OR_KEYWORD: int
    VAR_POSITIONAL: int
    KEYWORD_ONLY: int
    VAR_KEYWORD: int

    if sys.version_info >= (3, 8):
        @property
        def description(self) -> str: ...

class Parameter:
    def __init__(self, name: str, kind: _ParameterKind, *, default: Any = ..., annotation: Any = ...) -> None: ...
    empty = _empty

    POSITIONAL_ONLY: ClassVar[Literal[_ParameterKind.POSITIONAL_ONLY]]
    POSITIONAL_OR_KEYWORD: ClassVar[Literal[_ParameterKind.POSITIONAL_OR_KEYWORD]]
    VAR_POSITIONAL: ClassVar[Literal[_ParameterKind.VAR_POSITIONAL]]
    KEYWORD_ONLY: ClassVar[Literal[_ParameterKind.KEYWORD_ONLY]]
    VAR_KEYWORD: ClassVar[Literal[_ParameterKind.VAR_KEYWORD]]
    @property
    def name(self) -> str: ...
    @property
    def default(self) -> Any: ...
    @property
    def kind(self) -> _ParameterKind: ...
    @property
    def annotation(self) -> Any: ...
    def replace(
        self: Self,
        *,
        name: str | type[_void] = ...,
        kind: _ParameterKind | type[_void] = ...,
        default: Any = ...,
        annotation: Any = ...,
    ) -> Self: ...

class BoundArguments:
    arguments: OrderedDict[str, Any]
    args: tuple[Any, ...]
    kwargs: dict[str, Any]
    signature: Signature
    def __init__(self, signature: Signature, arguments: OrderedDict[str, Any]) -> None: ...
    def apply_defaults(self) -> None: ...

#
# Classes and functions
#

# TODO: The actual return type should be list[_ClassTreeItem] but mypy doesn't
# seem to be supporting this at the moment:
# _ClassTreeItem = list[_ClassTreeItem] | Tuple[type, Tuple[type, ...]]
def getclasstree(classes: list[type], unique: bool = ...) -> list[Any]: ...
def walktree(classes: list[type], children: dict[type[Any], list[type]], parent: type[Any] | None) -> list[Any]: ...

class Arguments(NamedTuple):
    args: list[str]
    varargs: str | None
    varkw: str | None

def getargs(co: CodeType) -> Arguments: ...

if sys.version_info < (3, 11):
    class ArgSpec(NamedTuple):
        args: list[str]
        varargs: str | None
        keywords: str | None
        defaults: tuple[Any, ...]
    def getargspec(func: object) -> ArgSpec: ...

class FullArgSpec(NamedTuple):
    args: list[str]
    varargs: str | None
    varkw: str | None
    defaults: tuple[Any, ...] | None
    kwonlyargs: list[str]
    kwonlydefaults: dict[str, Any] | None
    annotations: dict[str, Any]

def getfullargspec(func: object) -> FullArgSpec: ...

class ArgInfo(NamedTuple):
    args: list[str]
    varargs: str | None
    keywords: str | None
    locals: dict[str, Any]

def getargvalues(frame: FrameType) -> ArgInfo: ...
def formatannotation(annotation: object, base_module: str | None = ...) -> str: ...
def formatannotationrelativeto(object: object) -> Callable[[object], str]: ...

if sys.version_info < (3, 11):
    def formatargspec(
        args: list[str],
        varargs: str | None = ...,
        varkw: str | None = ...,
        defaults: tuple[Any, ...] | None = ...,
        kwonlyargs: Sequence[str] | None = ...,
        kwonlydefaults: dict[str, Any] | None = ...,
        annotations: dict[str, Any] = ...,
        formatarg: Callable[[str], str] = ...,
        formatvarargs: Callable[[str], str] = ...,
        formatvarkw: Callable[[str], str] = ...,
        formatvalue: Callable[[Any], str] = ...,
        formatreturns: Callable[[Any], str] = ...,
        formatannotation: Callable[[Any], str] = ...,
    ) -> str: ...

def formatargvalues(
    args: list[str],
    varargs: str | None,
    varkw: str | None,
    locals: dict[str, Any] | None,
    formatarg: Callable[[str], str] | None = ...,
    formatvarargs: Callable[[str], str] | None = ...,
    formatvarkw: Callable[[str], str] | None = ...,
    formatvalue: Callable[[Any], str] | None = ...,
) -> str: ...
def getmro(cls: type) -> tuple[type, ...]: ...
def getcallargs(__func: Callable[_P, Any], *args: _P.args, **kwds: _P.kwargs) -> dict[str, Any]: ...

class ClosureVars(NamedTuple):
    nonlocals: Mapping[str, Any]
    globals: Mapping[str, Any]
    builtins: Mapping[str, Any]
    unbound: AbstractSet[str]

def getclosurevars(func: Callable[..., Any]) -> ClosureVars: ...
def unwrap(func: Callable[..., Any], *, stop: Callable[[Any], Any] | None = ...) -> Any: ...

#
# The interpreter stack
#

class Traceback(NamedTuple):
    filename: str
    lineno: int
    function: str
    code_context: list[str] | None
    index: int | None  # type: ignore[assignment]

class FrameInfo(NamedTuple):
    frame: FrameType
    filename: str
    lineno: int
    function: str
    code_context: list[str] | None
    index: int | None  # type: ignore[assignment]

def getframeinfo(frame: FrameType | TracebackType, context: int = ...) -> Traceback: ...
def getouterframes(frame: Any, context: int = ...) -> list[FrameInfo]: ...
def getinnerframes(tb: TracebackType, context: int = ...) -> list[FrameInfo]: ...
def getlineno(frame: FrameType) -> int: ...
def currentframe() -> FrameType | None: ...
def stack(context: int = ...) -> list[FrameInfo]: ...
def trace(context: int = ...) -> list[FrameInfo]: ...

#
# Fetching attributes statically
#

def getattr_static(obj: object, attr: str, default: Any | None = ...) -> Any: ...

#
# Current State of Generators and Coroutines
#

GEN_CREATED: Literal["GEN_CREATED"]
GEN_RUNNING: Literal["GEN_RUNNING"]
GEN_SUSPENDED: Literal["GEN_SUSPENDED"]
GEN_CLOSED: Literal["GEN_CLOSED"]

def getgeneratorstate(
    generator: Generator[Any, Any, Any]
) -> Literal["GEN_CREATED", "GEN_RUNNING", "GEN_SUSPENDED", "GEN_CLOSED"]: ...

CORO_CREATED: Literal["CORO_CREATED"]
CORO_RUNNING: Literal["CORO_RUNNING"]
CORO_SUSPENDED: Literal["CORO_SUSPENDED"]
CORO_CLOSED: Literal["CORO_CLOSED"]

def getcoroutinestate(
    coroutine: Coroutine[Any, Any, Any]
) -> Literal["CORO_CREATED", "CORO_RUNNING", "CORO_SUSPENDED", "CORO_CLOSED"]: ...
def getgeneratorlocals(generator: Generator[Any, Any, Any]) -> dict[str, Any]: ...
def getcoroutinelocals(coroutine: Coroutine[Any, Any, Any]) -> dict[str, Any]: ...

# Create private type alias to avoid conflict with symbol of same
# name created in Attribute class.
_Object = object

class Attribute(NamedTuple):
    name: str
    kind: str
    defining_class: type
    object: _Object

def classify_class_attrs(cls: type) -> list[Attribute]: ...

if sys.version_info >= (3, 9):
    class ClassFoundException(Exception): ...
