from collections.abc import Callable, Iterable, Sequence
from typing import Any, Literal

from django import forms
from django.contrib.admin.filters import ListFilter
from django.contrib.admin.options import ModelAdmin, _DisplayT, _ListFilterT
from django.db.models.base import Model
from django.db.models.expressions import Expression
from django.db.models.options import Options
from django.db.models.query import QuerySet
from django.forms.formsets import BaseFormSet
from django.http.request import HttpRequest

ALL_VAR: str
ORDER_VAR: str
PAGE_VAR: str
SEARCH_VAR: str
ERROR_FLAG: str
IGNORED_PARAMS: tuple[str, ...]

class ChangeListSearchForm(forms.Form): ...

class ChangeList:
    search_form_class: type[forms.Form]
    model: type[Model]
    opts: Options
    lookup_opts: Options
    root_queryset: QuerySet
    list_display: _DisplayT
    list_display_links: _DisplayT
    list_filter: Sequence[_ListFilterT]
    date_hierarchy: Any
    search_fields: Sequence[str]
    search_help_text: str | None
    list_select_related: bool | Sequence[str]
    list_per_page: int
    list_max_show_all: int
    model_admin: ModelAdmin
    preserved_filters: str
    sortable_by: Sequence[str] | None
    page_num: int
    show_all: bool
    is_popup: bool
    add_facets: bool
    is_facets_optional: bool
    to_field: Any
    params: dict[str, Any]
    filter_params: dict[str, list[str]]
    remove_facet_link: str
    add_facet_link: str
    list_editable: Sequence[str]
    query: str
    queryset: Any
    title: str
    pk_attname: str
    formset: BaseFormSet | None
    def __init__(
        self,
        request: HttpRequest,
        model: type[Model],
        list_display: _DisplayT,
        list_display_links: _DisplayT,
        list_filter: Sequence[_ListFilterT],
        date_hierarchy: str | None,
        search_fields: Sequence[str],
        list_select_related: bool | Sequence[str],
        list_per_page: int,
        list_max_show_all: int,
        list_editable: Sequence[str],
        model_admin: ModelAdmin,
        sortable_by: Sequence[str] | None,
        search_help_text: str | None,
    ) -> None: ...
    def get_filters_params(self, params: dict[str, Any] | None = ...) -> dict[str, Any]: ...
    def get_filters(self, request: HttpRequest) -> tuple[list[ListFilter], bool, dict[str, bool | str], bool, bool]: ...
    def get_query_string(self, new_params: dict[str, Any] | None = ..., remove: Iterable[str] | None = ...) -> str: ...
    result_count: int
    show_full_result_count: bool
    show_admin_actions: bool
    full_result_count: int | None
    result_list: Any
    can_show_all: bool
    multi_page: bool
    paginator: Any
    def get_results(self, request: HttpRequest) -> None: ...
    def get_ordering_field(self, field_name: Callable | str) -> Expression | str | None: ...
    def get_ordering(self, request: HttpRequest, queryset: QuerySet) -> list[Expression | str]: ...
    def get_ordering_field_columns(self) -> dict[int, Literal["desc", "asc"]]: ...
    def get_queryset(self, request: HttpRequest, exclude_parameters: list[str | None] | None = ...) -> QuerySet: ...
    filter_specs: list[ListFilter]
    has_filters: bool
    has_active_filters: bool
    clear_all_filters_qs: str
    def apply_select_related(self, qs: QuerySet) -> QuerySet: ...
    def has_related_field_in_list_display(self) -> bool: ...
    def url_for_result(self, result: Model) -> str: ...
