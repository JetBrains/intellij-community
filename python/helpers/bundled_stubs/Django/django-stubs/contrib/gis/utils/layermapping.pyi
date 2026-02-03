from collections.abc import Mapping
from pathlib import Path
from typing import Any, Protocol, type_check_only

from django.contrib.gis.gdal import DataSource, OGRGeomType
from django.contrib.gis.gdal.field import Field as OGRField
from django.contrib.gis.gdal.layer import Layer
from django.db.backends.base.operations import BaseDatabaseOperations
from django.db.models import Field, Model

class LayerMapError(Exception): ...
class InvalidString(LayerMapError): ...
class InvalidDecimal(LayerMapError): ...
class InvalidInteger(LayerMapError): ...
class MissingForeignKey(LayerMapError): ...

@type_check_only
class _Writer(Protocol):
    def write(self, s: str, /) -> Any: ...

class LayerMapping:
    MULTI_TYPES: dict[int, OGRGeomType]
    FIELD_TYPES: dict[Field, OGRField | tuple[OGRField, ...]]
    ds: DataSource
    layer: Layer
    using: str
    spatial_backend: BaseDatabaseOperations
    mapping: Mapping[str, str]
    model: type[Model]
    geo_field: Any
    source_srs: Any
    transform: Any
    encoding: str | None
    unique: list[str] | tuple[str, ...] | str | None
    transaction_mode: Any
    transaction_decorator: Any
    def __init__(
        self,
        model: type[Model],
        data: str | Path | DataSource,
        mapping: Mapping[str, str],
        layer: int = ...,
        source_srs: Any | None = ...,
        encoding: str = ...,
        transaction_mode: str = ...,
        transform: bool = ...,
        unique: list[str] | tuple[str, ...] | str | None = ...,
        using: str | None = ...,
    ) -> None: ...
    def check_fid_range(self, fid_range: Any) -> Any: ...
    geom_field: str
    fields: dict[str, Field]
    coord_dim: int
    def check_layer(self) -> Any: ...
    def check_srs(self, source_srs: Any) -> Any: ...
    def check_unique(self, unique: Any) -> None: ...
    def feature_kwargs(self, feat: Any) -> Any: ...
    def unique_kwargs(self, kwargs: Any) -> Any: ...
    def verify_ogr_field(self, ogr_field: Any, model_field: Any) -> Any: ...
    def verify_fk(self, feat: Any, rel_model: Any, rel_mapping: Any) -> Any: ...
    def verify_geom(self, geom: Any, model_field: Any) -> Any: ...
    def coord_transform(self) -> Any: ...
    def geometry_field(self) -> Any: ...
    def make_multi(self, geom_type: Any, model_field: Any) -> Any: ...
    def save(
        self,
        verbose: bool = ...,
        fid_range: bool = ...,
        step: bool = ...,
        progress: bool = ...,
        silent: bool = ...,
        stream: _Writer = ...,
        strict: bool = ...,
    ) -> Any: ...
