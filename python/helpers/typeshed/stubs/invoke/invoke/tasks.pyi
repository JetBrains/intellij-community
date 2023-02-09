from _typeshed import Self
from collections.abc import Callable, Iterable
from typing import Any, TypeVar, overload

from .config import Config
from .context import Context
from .parser import Argument

_TaskT = TypeVar("_TaskT", bound=Task)

NO_DEFAULT: object

class Task:
    body: Callable[..., Any]
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
    pre: Iterable[Task]
    post: Iterable[Task]
    times_called: int
    autoprint: bool
    def __init__(
        self,
        body: Callable[..., Any],
        name: str | None = ...,
        aliases: tuple[str, ...] = ...,
        positional: Iterable[str] | None = ...,
        optional: Iterable[str] = ...,
        default: bool = ...,
        auto_shortflags: bool = ...,
        help: dict[str, str] | None = ...,
        pre: Iterable[Task] | None = ...,
        post: Iterable[Task] | None = ...,
        autoprint: bool = ...,
        iterable: Iterable[str] | None = ...,
        incrementable: Iterable[str] | None = ...,
    ) -> None: ...
    @property
    def name(self): ...
    def __eq__(self, other: Task) -> bool: ...  # type: ignore[override]
    def __hash__(self) -> int: ...
    def __call__(self, *args, **kwargs): ...
    @property
    def called(self) -> bool: ...
    def argspec(self, body): ...
    def fill_implicit_positionals(self, positional: Iterable[str] | None) -> Iterable[str]: ...
    def arg_opts(self, name: str, default: Any, taken_names: Iterable[str]) -> dict[str, Any]: ...
    def get_arguments(self, ignore_unknown_help: bool | None = ...) -> list[Argument]: ...

@overload
def task(__func: Callable[..., Any]) -> Task: ...
@overload
def task(
    *args: Task,
    name: str | None = ...,
    aliases: tuple[str, ...] = ...,
    positional: Iterable[str] | None = ...,
    optional: Iterable[str] = ...,
    default: bool = ...,
    auto_shortflags: bool = ...,
    help: dict[str, str] | None = ...,
    pre: list[Task] | None = ...,
    post: list[Task] | None = ...,
    autoprint: bool = ...,
    iterable: Iterable[str] | None = ...,
    incrementable: Iterable[str] | None = ...,
) -> Callable[[Callable[..., Any]], Task]: ...
@overload
def task(
    *args: Task,
    name: str | None = ...,
    aliases: tuple[str, ...] = ...,
    positional: Iterable[str] | None = ...,
    optional: Iterable[str] = ...,
    default: bool = ...,
    auto_shortflags: bool = ...,
    help: dict[str, str] | None = ...,
    pre: list[Task] | None = ...,
    post: list[Task] | None = ...,
    autoprint: bool = ...,
    iterable: Iterable[str] | None = ...,
    incrementable: Iterable[str] | None = ...,
    klass: type[_TaskT],
) -> Callable[[Callable[..., Any]], _TaskT]: ...

class Call:
    task: Task
    called_as: str | None
    args: tuple[Any, ...]
    kwargs: dict[str, Any]
    def __init__(
        self, task: Task, called_as: str | None = ..., args: tuple[Any, ...] | None = ..., kwargs: dict[str, Any] | None = ...
    ) -> None: ...
    def __getattr__(self, name: str) -> Any: ...
    def __deepcopy__(self: Self, memo: Any) -> Self: ...
    def __eq__(self, other: Call) -> bool: ...  # type: ignore[override]
    def make_context(self, config: Config) -> Context: ...
    def clone_data(self): ...
    # TODO use overload
    def clone(self, into: type[Call] | None = ..., with_: dict[str, Any] | None = ...) -> Call: ...

def call(task: Task, *args: Any, **kwargs: Any) -> Call: ...
