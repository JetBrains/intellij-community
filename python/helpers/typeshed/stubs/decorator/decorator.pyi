from builtins import dict as _dict  # alias to avoid conflicts with attribute name
from collections.abc import Callable, Iterator
from contextlib import _GeneratorContextManager
from inspect import getfullargspec as getfullargspec, iscoroutinefunction as iscoroutinefunction
from typing import Any, Pattern, TypeVar
from typing_extensions import ParamSpec

_C = TypeVar("_C", bound=Callable[..., Any])
_Func = TypeVar("_Func", bound=Callable[..., Any])
_T = TypeVar("_T")
_P = ParamSpec("_P")

def get_init(cls: type) -> None: ...

DEF: Pattern[str]

class FunctionMaker:
    args: list[str]
    varargs: str | None
    varkw: str | None
    defaults: tuple[Any, ...]
    kwonlyargs: list[str]
    kwonlydefaults: str | None
    shortsignature: str | None
    name: str
    doc: str | None
    module: str | None
    annotations: _dict[str, Any]
    signature: str
    dict: _dict[str, Any]
    def __init__(
        self,
        func: Callable[..., Any] | None = ...,
        name: str | None = ...,
        signature: str | None = ...,
        defaults: tuple[Any, ...] | None = ...,
        doc: str | None = ...,
        module: str | None = ...,
        funcdict: _dict[str, Any] | None = ...,
    ) -> None: ...
    def update(self, func: Any, **kw: Any) -> None: ...
    def make(
        self, src_templ: str, evaldict: _dict[str, Any] | None = ..., addsource: bool = ..., **attrs: Any
    ) -> Callable[..., Any]: ...
    @classmethod
    def create(
        cls,
        obj: Any,
        body: str,
        evaldict: _dict[str, Any],
        defaults: tuple[Any, ...] | None = ...,
        doc: str | None = ...,
        module: str | None = ...,
        addsource: bool = ...,
        **attrs: Any,
    ) -> Callable[..., Any]: ...

def decorate(func: _Func, caller: Callable[..., Any], extras: Any = ...) -> _Func: ...
def decorator(
    caller: Callable[..., Any], _func: Callable[..., Any] | None = ...
) -> Callable[[Callable[..., Any]], Callable[..., Any]]: ...

class ContextManager(_GeneratorContextManager[_T]):
    def __call__(self, func: _C) -> _C: ...

def contextmanager(func: Callable[_P, Iterator[_T]]) -> Callable[_P, ContextManager[_T]]: ...
def dispatch_on(*dispatch_args: Any) -> Callable[[Callable[..., Any]], Callable[..., Any]]: ...
