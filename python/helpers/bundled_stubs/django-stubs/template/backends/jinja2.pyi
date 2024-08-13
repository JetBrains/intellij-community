from collections.abc import Callable
from typing import Any

from _typeshed import Incomplete
from django.template.exceptions import TemplateSyntaxError
from django.utils.functional import cached_property

from .base import BaseEngine

class Jinja2(BaseEngine):
    env: Any
    context_processors: list[str]
    def __init__(self, params: dict[str, Any]) -> None: ...
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
    def render(self, context: Incomplete | None = ..., request: Incomplete | None = ...) -> Incomplete: ...

def get_exception_info(exception: TemplateSyntaxError) -> dict[str, Any]: ...
