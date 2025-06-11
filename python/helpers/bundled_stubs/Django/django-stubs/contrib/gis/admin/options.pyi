from typing import Any

from django.contrib.admin import ModelAdmin
from django.contrib.gis.forms import BaseGeometryWidget

class GISModelAdmin(ModelAdmin):
    gis_widget: BaseGeometryWidget
    gis_widget_kwargs: dict[str, Any]
