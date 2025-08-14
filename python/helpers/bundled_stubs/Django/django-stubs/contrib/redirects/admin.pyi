from typing import Any

from django.contrib import admin

class RedirectAdmin(admin.ModelAdmin):
    list_display: Any
    list_filter: Any
    search_fields: Any
    radio_fields: Any
