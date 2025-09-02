from typing import Any, ClassVar

from django.contrib import admin

class SiteAdmin(admin.ModelAdmin):
    list_display: Any
    search_fields: ClassVar[Any]
