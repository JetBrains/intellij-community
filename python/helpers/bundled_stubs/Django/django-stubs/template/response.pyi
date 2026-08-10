from collections.abc import Callable, Iterator, Sequence
from http.cookies import SimpleCookie
from typing import Any, TypeAlias

from django.http import HttpResponse
from django.http.request import HttpRequest
from typing_extensions import override

from .backends.base import _EngineTemplate

_TemplateForResponseT: TypeAlias = Sequence[str] | _EngineTemplate | str

class ContentNotRenderedError(Exception): ...

class SimpleTemplateResponse(HttpResponse):
    content: Any
    closed: bool
    cookies: SimpleCookie
    status_code: int
    rendering_attrs: Any
    template_name: _TemplateForResponseT
    context_data: dict[str, Any] | None
    using: str | None
    def __init__(
        self,
        template: _TemplateForResponseT,
        context: dict[str, Any] | None = ...,
        content_type: str | None = ...,
        status: int | None = ...,
        charset: str | None = ...,
        using: str | None = ...,
        headers: dict[str, Any] | None = ...,
    ) -> None: ...
    def resolve_template(self, template: _TemplateForResponseT) -> _EngineTemplate: ...
    def resolve_context(self, context: dict[str, Any] | None) -> dict[str, Any] | None: ...
    @property
    def rendered_content(self) -> str: ...
    def add_post_render_callback(self, callback: Callable[..., Any]) -> None: ...
    def render(self) -> SimpleTemplateResponse: ...
    @property
    def is_rendered(self) -> bool: ...
    @override
    def __iter__(self) -> Iterator[Any]: ...

class TemplateResponse(SimpleTemplateResponse):
    closed: bool
    context_data: dict[str, Any] | None
    cookies: SimpleCookie
    _request: HttpRequest
    status_code: int
    template_name: _TemplateForResponseT
    using: str | None
    rendering_attrs: Any
    def __init__(
        self,
        request: HttpRequest,
        template: _TemplateForResponseT,
        context: dict[str, Any] | None = ...,
        content_type: str | None = ...,
        status: int | None = ...,
        charset: str | None = ...,
        using: str | None = ...,
        headers: dict[str, Any] | None = ...,
    ) -> None: ...
