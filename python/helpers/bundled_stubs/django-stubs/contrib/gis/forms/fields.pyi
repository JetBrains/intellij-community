from typing import Any

from django import forms

class GeometryField(forms.Field):
    widget: Any
    geom_type: str
    srid: Any
    def __init__(self, *, srid: Any | None = ..., geom_type: Any | None = ..., **kwargs: Any) -> None: ...
    def to_python(self, value: Any) -> Any: ...
    def clean(self, value: Any) -> Any: ...
    def has_changed(self, initial: Any, data: Any) -> Any: ...

class GeometryCollectionField(GeometryField):
    geom_type: str

class PointField(GeometryField):
    geom_type: str

class MultiPointField(GeometryField):
    geom_type: str

class LineStringField(GeometryField):
    geom_type: str

class MultiLineStringField(GeometryField):
    geom_type: str

class PolygonField(GeometryField):
    geom_type: str

class MultiPolygonField(GeometryField):
    geom_type: str
