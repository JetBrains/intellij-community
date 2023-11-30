from _typeshed import Incomplete
from collections.abc import Callable, Iterable
from typing import Any, Generic, TypeVar, overload
from typing_extensions import ParamSpec, Self

from .config import Config
from .context import Context
from .parser import Argument

_P = ParamSpec("_P")
_R_co = TypeVar("_R_co", covariant=True)
_TaskT = TypeVar("_TaskT", bound=Task[..., Any])

class Task(Generic[_P, _R_co]):
    body: Callable[_P, _R_co]
    __doc__: str | None
    __name__: str
    __module__: str
    aliases: tuple[str, ...]
    is_default: bool
    positional: Iterable[str]
    optional: Iterable[str]
    iterable: Iterable[str]
    incrementable: Iterable[str]
    auto_shortflags: bool
    help: dict[str, str]
    pre: Iterable[Task[..., Any] | Call]
    post: Iterable[Task[..., Any] | Call]
    times_called: int
    autoprint: bool
    def __init__(
        self,
        body: Callable[..., Any],
        name: str | None = None,
        aliases: tuple[str, ...] = (),
        positional: Iterable[str] | None = None,
        optional: Iterable[str] = (),
        default: bool = False,
        auto_shortflags: bool = True,
        help: dict[str, str] | None = None,
        pre: Iterable[Task[..., Any] | Call] | None = None,
        post: Iterable[Task[..., Any] | Call] | None = None,
        autoprint: bool = False,
        iterable: Iterable[str] | None = None,
        incrementable: Iterable[str] | None = None,
    ) -> None: ...
    @property
    def name(self): ...
    def __eq__(self, other: Task[Incomplete, Incomplete]) -> bool: ...  # type: ignore[override]
    def __hash__(self) -> int: ...
    def __call__(self, *args: _P.args, **kwargs: _P.kwargs) -> _R_co: ...
    @property
    def called(self) -> bool: ...
    def argspec(self, body): ...
    def fill_implicit_positionals(self, positional: Iterable[str] | None) -> Iterable[str]: ...
    def arg_opts(self, name: str, default: Any, taken_names: Iterable[str]) -> dict[str, Any]: ...
    def get_arguments(self, ignore_unknown_help: bool | None = None) -> list[Argument]: ...

@overload
def task(  # type: ignore[misc]
    *args: Task[..., Any] | Call,
    name: str | None = ...,
    aliases: tuple[str, ...] = ...,
    positional: Iterable[str] | None = ...,
    optional: Iterable[str] = ...,
    default: bool = ...,
    auto_shortflags: bool = ...,
    help: dict[str, str] | None = ...,
    pre: list[Task[..., Any] | Call] | None = ...,
    post: list[Task[..., Any] | Call] | None = ...,
    autoprint: bool = ...,
    iterable: Iterable[str] | None = ...,
    incrementable: Iterable[str] | None = ...,
) -> Callable[[Callable[_P, _R_co]], Task[_P, _R_co]]: ...
@overload
def task(
    *args: Task[..., Any] | Call,
    name: str | None = ...,
    aliases: tuple[str, ...] = ...,
    positional: Iterable[str] | None = ...,
    optional: Iterable[str] = ...,
    default: bool = ...,
    auto_shortflags: bool = ...,
    help: dict[str, str] | None = ...,
    pre: list[Task[..., Any] | Call] | None = ...,
    post: list[Task[..., Any] | Call] | None = ...,
    autoprint: bool = ...,
    iterable: Iterable[str] | None = ...,
    incrementable: Iterable[str] | None = ...,
    klass: type[_TaskT],
) -> Callable[[Callable[..., Any]], _TaskT]: ...
@overload
def task(__func: Callable[_P, _R_co]) -> Task[_P, _R_co]: ...

class Call:
    task: Task[..., Any]
    called_as: str | None
    args: tuple[Any, ...]
    kwargs: dict[str, Any]
    def __init__(
        self,
        task: Task[..., Any],
        called_as: str | None = None,
        args: tuple[Any, ...] | None = None,
        kwargs: dict[str, Any] | None = None,
    ) -> None: ...
    def __getattr__(self, name: str) -> Any: ...
    def __deepcopy__(self, memo: Any) -> Self: ...
    def __eq__(self, other: Call) -> bool: ...  # type: ignore[override]
    def make_context(self, config: Config) -> Context: ...
    def clone_data(self): ...
    # TODO use overload
    def clone(self, into: type[Call] | None = None, with_: dict[str, Any] | None = None) -> Call: ...

def call(task: Task[..., Any], *args: Any, **kwargs: Any) -> Call: ...
