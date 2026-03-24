from logging import Logger
from typing import Any

from django.forms.widgets import Widget
from typing_extensions import override

logger: Logger

class BaseGeometryWidget(Widget):
    base_layer: str | None
    geom_type: str
    map_srid: int
    display_raw: bool
    supports_3d: bool
    template_name: str
    attrs: Any
    def __init__(self, attrs: Any | None = ...) -> None: ...
    def serialize(self, value: Any) -> Any: ...
    def deserialize(self, value: Any) -> Any: ...
    @override
    def get_context(self, name: Any, value: Any, attrs: Any) -> Any: ...

class OpenLayersWidget(BaseGeometryWidget):
    base_layer: str
    template_name: str
    map_srid: int

    class Media:
        css: Any
        js: Any

    @override
    def serialize(self, value: Any) -> Any: ...
    @override
    def deserialize(self, value: Any) -> Any: ...

class OSMWidget(OpenLayersWidget):
    base_layer: str
    template_name: str
    default_lon: int
    default_lat: int
    default_zoom: int
    def __init__(self, attrs: Any | None = ...) -> None: ...
