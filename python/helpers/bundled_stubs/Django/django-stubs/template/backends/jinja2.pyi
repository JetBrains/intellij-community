from collections.abc import Callable
from typing import Any

from _typeshed import Incomplete
from django.http.request import HttpRequest
from django.template.exceptions import TemplateSyntaxError
from django.utils.functional import cached_property

from .base import BaseEngine

class Jinja2(BaseEngine):
    env: Any
    context_processors: list[str]
    def __init__(self, params: dict[str, Any]) -> None: ...
    def from_string(self, template_code: str) -> Template: ...
    def get_template(self, template_name: str) -> Template: ...
    @cached_property
    def template_context_processors(self) -> list[Callable]: ...

class Origin:
    name: str
    template_name: str | None
    def __init__(self, name: str, template_name: str | None) -> None: ...

class Template:
    template: Incomplete
    backend: Jinja2
    origin: Origin
    def __init__(self, template: Incomplete, backend: Jinja2) -> None: ...
    def render(self, context: dict[str, Any] | None = ..., request: HttpRequest | None = ...) -> str: ...

def get_exception_info(exception: TemplateSyntaxError) -> dict[str, Any]: ...
