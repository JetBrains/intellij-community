from typing import Any

from django.contrib.admin import ModelAdmin
from django.contrib.gis.forms import OSMWidget
from django.db.models import Model
from django.db.models.fields import Field
from django.forms.fields import Field as FormField
from django.http.request import HttpRequest
from typing_extensions import TypeVar

_ModelT = TypeVar("_ModelT", bound=Model)

class GeoModelAdminMixin:
    gis_widget: type[OSMWidget]
    gis_widget_kwargs: dict[str, Any]
    def formfield_for_dbfield(self, db_field: Field, request: HttpRequest, **kwargs: Any) -> FormField | None: ...

class GISModelAdmin(GeoModelAdminMixin, ModelAdmin[_ModelT]): ...
