from collections.abc import Sequence
from typing import Any, Generic, Protocol, TypeVar, overload, type_check_only

from django.core.paginator import Page, Paginator, _SupportsPagination
from django.db.models import Model, QuerySet
from django.http import HttpRequest, HttpResponse
from django.views.generic.base import ContextMixin, TemplateResponseMixin, View

_M = TypeVar("_M", bound=Model)

@type_check_only
class _HasModel(Protocol):
    @property
    def model(self) -> type[Model]: ...

class MultipleObjectMixin(Generic[_M], ContextMixin):
    allow_empty: bool
    queryset: QuerySet[_M] | None
    model: type[_M] | None
    paginate_by: int | None
    paginate_orphans: int
    context_object_name: str | None
    paginator_class: type[Paginator]
    page_kwarg: str
    ordering: str | Sequence[str] | None
    def get_queryset(self) -> QuerySet[_M]: ...
    def get_ordering(self) -> str | Sequence[str] | None: ...
    def paginate_queryset(
        self, queryset: _SupportsPagination[_M], page_size: int
    ) -> tuple[Paginator, Page, _SupportsPagination[_M], bool]: ...
    def get_paginate_by(self, queryset: QuerySet[_M]) -> int | None: ...
    def get_paginator(
        self,
        queryset: _SupportsPagination[_M],
        per_page: int,
        orphans: int = ...,
        allow_empty_first_page: bool = ...,
        **kwargs: Any,
    ) -> Paginator: ...
    def get_paginate_orphans(self) -> int: ...
    def get_allow_empty(self) -> bool: ...
    @overload
    def get_context_object_name(self, object_list: _HasModel) -> str: ...
    @overload
    def get_context_object_name(self, object_list: Any) -> str | None: ...
    def get_context_data(
        self, *, object_list: _SupportsPagination[_M] | None = ..., **kwargs: Any
    ) -> dict[str, Any]: ...

class BaseListView(MultipleObjectMixin[_M], View):
    object_list: _SupportsPagination[_M]
    def get(self, request: HttpRequest, *args: Any, **kwargs: Any) -> HttpResponse: ...

class MultipleObjectTemplateResponseMixin(TemplateResponseMixin):
    template_name_suffix: str

class ListView(MultipleObjectTemplateResponseMixin, BaseListView[_M]): ...
