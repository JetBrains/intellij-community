from typing import Any

from django.forms.boundfield import BoundField
from django.http import HttpRequest
from django.template.backends.base import BaseEngine
from django.template.backends.django import DjangoTemplates as DjangoTemplatesR
from django.template.backends.jinja2 import Jinja2 as Jinja2R
from django.template.base import Template
from django.utils.functional import cached_property
from typing_extensions import deprecated

def get_default_renderer() -> BaseRenderer: ...

class BaseRenderer:
    form_template_name: str
    formset_template_name: str
    field_template_name: str
    bound_field_class: type[BoundField] | None
    def get_template(self, template_name: str) -> Any: ...
    def render(self, template_name: str, context: dict[str, Any], request: HttpRequest | None = None) -> str: ...

class EngineMixin:
    def get_template(self, template_name: str) -> Any: ...
    @cached_property
    def engine(self) -> BaseEngine: ...

class DjangoTemplates(EngineMixin, BaseRenderer):
    backend: type[DjangoTemplatesR]

class Jinja2(EngineMixin, BaseRenderer):
    @cached_property
    def backend(self) -> type[Jinja2R]: ...

@deprecated(
    "Jinja2DivFormRenderer transitional form renderer is deprecated and will be removed in Django 6.0. Use Jinja2 instead."
)
class Jinja2DivFormRenderer(Jinja2):
    form_template_name: str
    formset_template_name: str

class TemplatesSetting(BaseRenderer):
    def get_template(self, template_name: str) -> Template | None: ...
