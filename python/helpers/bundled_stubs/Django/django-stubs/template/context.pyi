from collections.abc import Callable, Iterable, Iterator
from contextlib import contextmanager
from types import TracebackType
from typing import Any

from django.http.request import HttpRequest
from django.template.base import Node, Origin, Template
from django.template.loader_tags import IncludeNode
from typing_extensions import Self, TypeAlias

_ContextKeys: TypeAlias = int | str | Node

_ContextValues: TypeAlias = dict[str, Any] | Context

class ContextPopException(Exception): ...

class ContextDict(dict):
    context: BaseContext
    def __init__(self, context: BaseContext, *args: Any, **kwargs: Any) -> None: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        exc_tb: TracebackType | None,
    ) -> None: ...

class BaseContext(Iterable[Any]):
    def __init__(self, dict_: Any | None = None) -> None: ...
    def __copy__(self) -> Self: ...
    def __iter__(self) -> Iterator[Any]: ...
    def push(self, *args: Any, **kwargs: Any) -> ContextDict: ...
    def pop(self) -> ContextDict: ...
    def __setitem__(self, key: _ContextKeys, value: Any) -> None: ...
    def set_upward(self, key: _ContextKeys, value: int | str) -> None: ...
    def __getitem__(self, key: _ContextKeys) -> Any: ...
    def __delitem__(self, key: _ContextKeys) -> None: ...
    def __contains__(self, key: _ContextKeys) -> bool: ...
    def get(self, key: _ContextKeys, otherwise: Any | None = None) -> Any | None: ...
    def setdefault(self, key: _ContextKeys, default: list[Origin] | int | None = None) -> list[Origin] | int | None: ...
    def new(self, values: _ContextValues | None = None) -> Context: ...
    def flatten(self) -> dict[_ContextKeys, dict[_ContextKeys, type[Any] | str] | int | str | None]: ...

class Context(BaseContext):
    dicts: Any
    autoescape: bool
    use_l10n: bool | None
    use_tz: bool | None
    template_name: str | None
    render_context: RenderContext
    template: Template | None
    def __init__(
        self,
        dict_: Any | None = None,
        autoescape: bool = True,
        use_l10n: bool | None = None,
        use_tz: bool | None = None,
    ) -> None: ...
    @contextmanager
    def bind_template(self, template: Template) -> Iterator[None]: ...
    def update(self, other_dict: dict[str, Any] | Context) -> ContextDict: ...

class RenderContext(BaseContext):
    dicts: list[dict[IncludeNode | str, str]]
    template: Template | None
    @contextmanager
    def push_state(self, template: Template, isolated_context: bool = True) -> Iterator[None]: ...

class RequestContext(Context):
    autoescape: bool
    dicts: list[dict[str, str]]
    render_context: RenderContext
    template_name: str | None
    use_l10n: bool | None
    use_tz: bool | None
    request: HttpRequest
    def __init__(
        self,
        request: HttpRequest,
        dict_: dict[str, Any] | None = None,
        processors: list[Callable] | None = None,
        use_l10n: bool | None = None,
        use_tz: bool | None = None,
        autoescape: bool = True,
    ) -> None: ...
    template: Template | None
    @contextmanager
    def bind_template(self, template: Template) -> Iterator[None]: ...
    def new(self, values: _ContextValues | None = None) -> RequestContext: ...

def make_context(context: dict[str, Any] | None, request: HttpRequest | None = None, **kwargs: Any) -> Context: ...
