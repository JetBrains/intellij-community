from typing import Any, Generic, TypeVar

from django.db import models
from django.http import HttpRequest, HttpResponse
from django.views.generic.base import ContextMixin, TemplateResponseMixin, View

_M = TypeVar("_M", bound=models.Model)

class SingleObjectMixin(Generic[_M], ContextMixin):
    model: type[_M]
    queryset: models.query.QuerySet[_M] | None
    slug_field: str
    context_object_name: str | None
    slug_url_kwarg: str
    pk_url_kwarg: str
    query_pk_and_slug: bool
    def get_object(self, queryset: models.query.QuerySet[_M] | None = ...) -> _M: ...
    def get_queryset(self) -> models.query.QuerySet[_M]: ...
    def get_slug_field(self) -> str: ...
    def get_context_object_name(self, obj: _M) -> str | None: ...

class BaseDetailView(SingleObjectMixin[_M], View):
    object: _M
    def get(self, request: HttpRequest, *args: Any, **kwargs: Any) -> HttpResponse: ...

class SingleObjectTemplateResponseMixin(TemplateResponseMixin):
    template_name_field: str | None
    template_name_suffix: str

class DetailView(SingleObjectTemplateResponseMixin, BaseDetailView[_M]): ...
