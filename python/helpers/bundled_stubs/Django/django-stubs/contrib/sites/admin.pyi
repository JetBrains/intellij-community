from typing import Any, ClassVar

from django.contrib import admin
from django.contrib.sites.models import Site

class SiteAdmin(admin.ModelAdmin[Site]):
    list_display: Any
    search_fields: ClassVar[Any]
