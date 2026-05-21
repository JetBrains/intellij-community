from collections.abc import Callable
from typing import Any

from django.http.request import HttpRequest
from django.template.exceptions import TemplateSyntaxError
from django.utils.functional import cached_property
from jinja2 import Environment
from jinja2 import Template as Jinja2Template
from typing_extensions import override

from .base import BaseEngine

class Jinja2(BaseEngine):
    env: Environment
    context_processors: list[str]
    def __init__(self, params: dict[str, Any]) -> None: ...
    @override
    def from_string(self, template_code: str) -> Template: ...
    @override
    def get_template(self, template_name: str) -> Template: ...
    @cached_property
    def template_context_processors(self) -> list[Callable[[HttpRequest], dict[str, Any]]]: ...

class Template:
    template: Jinja2Template
    backend: Jinja2
    origin: Origin
    def __init__(self, template: Jinja2Template, backend: Jinja2) -> None: ...
    def render(self, context: dict[str, Any] | None = None, request: HttpRequest | None = None) -> str: ...

class Origin:
    name: str
    template_name: str | None
    def __init__(self, name: str, template_name: str | None) -> None: ...

def get_exception_info(exception: TemplateSyntaxError) -> dict[str, Any]: ...
