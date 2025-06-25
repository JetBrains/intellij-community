from typing import Any

from django.contrib import admin

class SiteAdmin(admin.ModelAdmin):
    list_display: Any
    search_fields: Any
