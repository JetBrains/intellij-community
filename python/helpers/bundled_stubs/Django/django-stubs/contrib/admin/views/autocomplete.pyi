from typing import Any

from django.contrib.admin.options import ModelAdmin
from django.db.models import Model
from django.http.request import HttpRequest
from django.views.generic.list import BaseListView

class AutocompleteJsonView(BaseListView):
    model_admin: ModelAdmin
    term: Any
    def has_perm(self, request: HttpRequest, obj: Model | None = ...) -> bool: ...
