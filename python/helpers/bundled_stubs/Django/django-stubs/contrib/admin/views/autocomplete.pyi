from typing import Any

from django.contrib.admin.options import ModelAdmin
from django.contrib.admin.sites import AdminSite
from django.db.models import Model
from django.db.models.fields.related import ForeignKey
from django.http.request import HttpRequest
from django.views.generic.list import BaseListView

class AutocompleteJsonView(BaseListView[Any]):
    admin_site: AdminSite | None
    model_admin: ModelAdmin[Any]
    source_field: ForeignKey[Any, Any]
    term: str
    def serialize_result(self, obj: Model, to_field_name: str) -> dict[str, str]: ...
    def process_request(self, request: HttpRequest) -> tuple[str, ModelAdmin[Any], ForeignKey[Any, Any], str]: ...
    def has_perm(self, request: HttpRequest, obj: Model | None = ...) -> bool: ...
