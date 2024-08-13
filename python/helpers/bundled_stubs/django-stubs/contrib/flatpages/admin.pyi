from typing import Any

from django.contrib import admin
from django.contrib.flatpages.models import FlatPage

class FlatPageAdmin(admin.ModelAdmin[FlatPage]):
    form: Any
    fieldsets: Any
    list_display: Any
    list_filter: Any
    search_fields: Any
