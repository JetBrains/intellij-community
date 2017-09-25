from typing import Any, Callable, IO, Iterable, List, Optional, TypeVar, Union
import uuid

from click.core import Context, Parameter


class ParamType:
    name: str
    is_composite: bool
    envvar_list_splitter: Optional[str]

    def __call__(
        self,
        value: Optional[str],
        param: Optional[Parameter] = None,
        ctx: Optional[Context] = None,
    ) -> Any:
        ...

    def get_metavar(self, param: Parameter) -> str:
        ...

    def get_missing_message(self, param: Parameter) -> str:
        ...

    def convert(
        self,
        value: str,
        param: Optional[Parameter],
        ctx: Optional[Context],
    ) -> Any:
        ...

    def split_envvar_value(self, rv: str) -> List[str]:
        ...

    def fail(self, message: str, param: Optional[Parameter] = None, ctx: Optional[Context] = None) -> None:
        ...


class BoolParamType(ParamType):
    def __call__(
        self,
        value: Optional[str],
        param: Optional[Parameter] = None,
        ctx: Optional[Context] = None,
    ) -> bool:
        ...

    def convert(
        self,
        value: str,
        param: Optional[Parameter],
        ctx: Optional[Context],
    ) -> bool:
        ...


class CompositeParamType(ParamType):
    arity: int


class Choice(ParamType):
    choices: Iterable[str]
    def __init__(self, choices: Iterable[str]) -> None:
        ...


class FloatParamType(ParamType):
    def __call__(
        self,
        value: Optional[str],
        param: Optional[Parameter] = None,
        ctx: Optional[Context] = None,
    ) -> float:
        ...

    def convert(
        self,
        value: str,
        param: Optional[Parameter],
        ctx: Optional[Context],
    ) -> float:
        ...


class FloatRange(FloatParamType):
    ...


class File(ParamType):
    def __init__(
        self,
        mode: str = 'r',
        encoding: Optional[str] = None,
        errors: Optional[str] = None,
        lazy: Optional[bool] = None,
        atomic: Optional[bool] = None,
    ) -> None:
        ...

    def __call__(
        self,
        value: Optional[str],
        param: Optional[Parameter] = None,
        ctx: Optional[Context] = None,
    ) -> IO:
        ...

    def convert(
        self,
        value: str,
        param: Optional[Parameter],
        ctx: Optional[Context],
    ) -> IO:
        ...

    def resolve_lazy_flag(self, value: str) -> bool:
        ...


_F = TypeVar('_F')  # result of the function
_Func = Callable[[Optional[str]], _F]


class FuncParamType(ParamType):
    func: _Func

    def __init__(self, func: _Func) -> None:
        ...

    def __call__(
        self,
        value: Optional[str],
        param: Optional[Parameter] = None,
        ctx: Optional[Context] = None,
    ) -> _F:
        ...

    def convert(
        self,
        value: str,
        param: Optional[Parameter],
        ctx: Optional[Context],
    ) -> _F:
        ...


class IntParamType(ParamType):
    def __call__(
        self,
        value: Optional[str],
        param: Optional[Parameter] = None,
        ctx: Optional[Context] = None,
    ) -> int:
        ...

    def convert(
        self,
        value: str,
        param: Optional[Parameter],
        ctx: Optional[Context],
    ) -> int:
        ...


class IntRange(IntParamType):
    def __init__(
        self, min: Optional[int] = None, max: Optional[int] = None, clamp: bool = False
    ) -> None:
        ...


_PathType = TypeVar('_PathType', str, bytes)


class Path(ParamType):
    def __init__(
        self,
        exists: bool = False,
        file_okay: bool = True,
        dir_okay: bool = True,
        writable: bool = False,
        readable: bool = True,
        resolve_path: bool = False,
        allow_dash: bool = False,
        path_type: Optional[_PathType] = None,
    ) -> None:
        ...

    def coerce_path_result(self, rv: Union[str, bytes]) -> _PathType:
        ...

    def __call__(
        self,
        value: Optional[str],
        param: Optional[Parameter] = None,
        ctx: Optional[Context] = None,
    ) -> _PathType:
        ...

    def convert(
        self,
        value: str,
        param: Optional[Parameter],
        ctx: Optional[Context],
    ) -> _PathType:
        ...

class StringParamType(ParamType):
    def __call__(
        self,
        value: Optional[str],
        param: Optional[Parameter] = None,
        ctx: Optional[Context] = None,
    ) -> str:
        ...

    def convert(
        self,
        value: str,
        param: Optional[Parameter],
        ctx: Optional[Context],
    ) -> str:
        ...


class Tuple(CompositeParamType):
    types: List[ParamType]

    def __init__(self, types: Iterable[Any]) -> None:
        ...

    def __call__(
        self,
        value: Optional[str],
        param: Optional[Parameter] = None,
        ctx: Optional[Context] = None,
    ) -> Tuple:
        ...

    def convert(
        self,
        value: str,
        param: Optional[Parameter],
        ctx: Optional[Context],
    ) -> Tuple:
        ...


class UnprocessedParamType(ParamType):
    ...


class UUIDParameterType(ParamType):
    def __call__(
        self,
        value: Optional[str],
        param: Optional[Parameter] = None,
        ctx: Optional[Context] = None,
    ) -> uuid.UUID:
        ...

    def convert(
        self,
        value: str,
        param: Optional[Parameter],
        ctx: Optional[Context],
    ) -> uuid.UUID:
        ...


def convert_type(ty: Any, default: Optional[Any] = None) -> ParamType:
    ...

# parameter type shortcuts

BOOL = BoolParamType()
FLOAT = FloatParamType()
INT = IntParamType()
STRING = StringParamType()
UNPROCESSED = UnprocessedParamType()
UUID = UUIDParameterType()
