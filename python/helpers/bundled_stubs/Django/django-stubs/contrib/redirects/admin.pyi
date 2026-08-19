from typing import Any, ClassVar

from django.contrib import admin
from django.contrib.redirects.models import Redirect

class RedirectAdmin(admin.ModelAdmin[Redirect]):
    list_display: Any
    list_filter: ClassVar[Any]
    search_fields: ClassVar[Any]
    radio_fields: ClassVar[Any]
