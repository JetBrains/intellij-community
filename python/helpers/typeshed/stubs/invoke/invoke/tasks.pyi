from _typeshed import Self
from typing import Any

from .config import Config
from .context import Context

NO_DEFAULT: object

class Task:
    body: Any
    __doc__: str
    __name__: str
    __module__: Any
    aliases: Any
    is_default: bool
    positional: Any
    optional: Any
    iterable: Any
    incrementable: Any
    auto_shortflags: Any
    help: Any
    pre: Any
    post: Any
    times_called: int
    autoprint: Any
    def __init__(
        self,
        body,
        name=...,
        aliases=...,
        positional=...,
        optional=...,
        default: bool = ...,
        auto_shortflags: bool = ...,
        help=...,
        pre=...,
        post=...,
        autoprint: bool = ...,
        iterable=...,
        incrementable=...,
    ) -> None: ...
    @property
    def name(self): ...
    def __eq__(self, other): ...
    def __hash__(self): ...
    def __call__(self, *args, **kwargs): ...
    @property
    def called(self): ...
    def argspec(self, body): ...
    def fill_implicit_positionals(self, positional): ...
    def arg_opts(self, name, default, taken_names): ...
    def get_arguments(self): ...

def task(*args, **kwargs) -> Task: ...

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
