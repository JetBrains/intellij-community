import string
from typing import Any

from django.http.request import HttpRequest

from .base import BaseEngine

class TemplateStrings(BaseEngine):
    def __init__(self, params: dict[str, dict[Any, Any] | list[Any] | bool | str]) -> None: ...
    def from_string(self, template_code: str) -> Template: ...
    def get_template(self, template_name: str) -> Template: ...

class Template(string.Template):
    template: str
    def render(self, context: dict[str, Any] | None = ..., request: HttpRequest | None = ...) -> str: ...
