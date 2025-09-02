from typing import Any, ClassVar

from django.contrib import admin
from django.contrib.flatpages.models import FlatPage

class FlatPageAdmin(admin.ModelAdmin[FlatPage]):
    form: Any
    fieldsets: ClassVar[Any]
    list_display: Any
    list_filter: ClassVar[Any]
    search_fields: ClassVar[Any]
