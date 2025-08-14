import functools
from collections.abc import Callable, Iterator, Sequence
from http.cookies import SimpleCookie
from typing import Any

from django.core.handlers.wsgi import WSGIRequest
from django.http import HttpResponse
from django.http.request import HttpRequest
from django.template.base import Template
from django.template.context import RequestContext
from django.test.client import Client
from django.utils.datastructures import _ListOrTuple
from typing_extensions import TypeAlias

_TemplateForResponseT: TypeAlias = _ListOrTuple[str] | Template | str

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
    def resolve_template(self, template: Sequence[str] | Template | str) -> Template: ...
    def resolve_context(self, context: dict[str, Any] | None) -> dict[str, Any] | None: ...
    @property
    def rendered_content(self) -> str: ...
    def add_post_render_callback(self, callback: Callable) -> None: ...
    def render(self) -> SimpleTemplateResponse: ...
    @property
    def is_rendered(self) -> bool: ...
    def __iter__(self) -> Iterator[Any]: ...

class TemplateResponse(SimpleTemplateResponse):
    client: Client
    closed: bool
    context: RequestContext
    context_data: dict[str, Any] | None
    cookies: SimpleCookie
    csrf_cookie_set: bool
    json: functools.partial
    _request: HttpRequest
    status_code: int
    template_name: _TemplateForResponseT
    templates: list[Template]
    using: str | None
    wsgi_request: WSGIRequest
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
