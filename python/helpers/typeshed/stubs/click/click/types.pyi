import datetime
import uuid
from typing import IO, Any, Callable, Generic, Iterable, Optional, Sequence, Text, Tuple as _PyTuple, Type, TypeVar, Union

from click.core import Context, Parameter, _ConvertibleType, _ParamType

ParamType = _ParamType

class BoolParamType(ParamType):
    def __call__(self, value: str | None, param: Parameter | None = ..., ctx: Context | None = ...) -> bool: ...
    def convert(self, value: str, param: Parameter | None, ctx: Context | None) -> bool: ...

class CompositeParamType(ParamType):
    arity: int

class Choice(ParamType):
    choices: Iterable[str]
    case_sensitive: bool
    def __init__(self, choices: Iterable[str], case_sensitive: bool = ...) -> None: ...

class DateTime(ParamType):
    formats: Sequence[str]
    def __init__(self, formats: Sequence[str] | None = ...) -> None: ...
    def convert(self, value: str, param: Parameter | None, ctx: Context | None) -> datetime.datetime: ...

class FloatParamType(ParamType):
    def __call__(self, value: str | None, param: Parameter | None = ..., ctx: Context | None = ...) -> float: ...
    def convert(self, value: str, param: Parameter | None, ctx: Context | None) -> float: ...

class FloatRange(FloatParamType):
    min: float | None
    max: float | None
    clamp: bool
    def __init__(self, min: float | None = ..., max: float | None = ..., clamp: bool = ...) -> None: ...

class File(ParamType):
    mode: str
    encoding: str | None
    errors: str | None
    lazy: bool | None
    atomic: bool
    def __init__(
        self,
        mode: Text = ...,
        encoding: str | None = ...,
        errors: str | None = ...,
        lazy: bool | None = ...,
        atomic: bool | None = ...,
    ) -> None: ...
    def __call__(self, value: str | None, param: Parameter | None = ..., ctx: Context | None = ...) -> IO[Any]: ...
    def convert(self, value: str, param: Parameter | None, ctx: Context | None) -> IO[Any]: ...
    def resolve_lazy_flag(self, value: str) -> bool: ...

_F = TypeVar("_F")  # result of the function
_Func = Callable[[Optional[str]], _F]

class FuncParamType(ParamType, Generic[_F]):
    func: _Func[_F]
    def __init__(self, func: _Func[_F]) -> None: ...
    def __call__(self, value: str | None, param: Parameter | None = ..., ctx: Context | None = ...) -> _F: ...
    def convert(self, value: str, param: Parameter | None, ctx: Context | None) -> _F: ...

class IntParamType(ParamType):
    def __call__(self, value: str | None, param: Parameter | None = ..., ctx: Context | None = ...) -> int: ...
    def convert(self, value: str, param: Parameter | None, ctx: Context | None) -> int: ...

class IntRange(IntParamType):
    min: int | None
    max: int | None
    clamp: bool
    def __init__(self, min: int | None = ..., max: int | None = ..., clamp: bool = ...) -> None: ...

_PathType = TypeVar("_PathType", str, bytes)
_PathTypeBound = Union[Type[str], Type[bytes]]

class Path(ParamType):
    exists: bool
    file_okay: bool
    dir_okay: bool
    writable: bool
    readable: bool
    resolve_path: bool
    allow_dash: bool
    type: _PathTypeBound | None
    def __init__(
        self,
        exists: bool = ...,
        file_okay: bool = ...,
        dir_okay: bool = ...,
        writable: bool = ...,
        readable: bool = ...,
        resolve_path: bool = ...,
        allow_dash: bool = ...,
        path_type: Type[_PathType] | None = ...,
    ) -> None: ...
    def coerce_path_result(self, rv: str | bytes) -> _PathType: ...
    def __call__(self, value: str | None, param: Parameter | None = ..., ctx: Context | None = ...) -> _PathType: ...
    def convert(self, value: str, param: Parameter | None, ctx: Context | None) -> _PathType: ...

class StringParamType(ParamType):
    def __call__(self, value: str | None, param: Parameter | None = ..., ctx: Context | None = ...) -> str: ...
    def convert(self, value: str, param: Parameter | None, ctx: Context | None) -> str: ...

class Tuple(CompositeParamType):
    types: list[ParamType]
    def __init__(self, types: Iterable[Any]) -> None: ...
    def __call__(self, value: str | None, param: Parameter | None = ..., ctx: Context | None = ...) -> Tuple: ...
    def convert(self, value: str, param: Parameter | None, ctx: Context | None) -> Tuple: ...

class UnprocessedParamType(ParamType): ...

class UUIDParameterType(ParamType):
    def __call__(self, value: str | None, param: Parameter | None = ..., ctx: Context | None = ...) -> uuid.UUID: ...
    def convert(self, value: str, param: Parameter | None, ctx: Context | None) -> uuid.UUID: ...

def convert_type(ty: _ConvertibleType | None, default: Any | None = ...) -> ParamType: ...

# parameter type shortcuts

BOOL: BoolParamType
FLOAT: FloatParamType
INT: IntParamType
STRING: StringParamType
UNPROCESSED: UnprocessedParamType
UUID: UUIDParameterType
