from typing import Any

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import Aggregate
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from typing_extensions import override

__all__ = ["Collect", "Extent", "Extent3D", "MakeLine", "Union"]

class GeoAggregate(Aggregate):
    is_extent: bool | str
    @override
    def as_sql(  # type: ignore[override]
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, function: str | None = None, **extra_context: Any
    ) -> _AsSqlType: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class Collect(GeoAggregate):
    name: str
    output_field_class: Any

class Extent(GeoAggregate):
    name: str
    is_extent: str
    def __init__(self, expression: Any, **extra: Any) -> None: ...

class Extent3D(GeoAggregate):
    name: str
    is_extent: str
    def __init__(self, expression: Any, **extra: Any) -> None: ...

class MakeLine(GeoAggregate):
    name: str
    output_field_class: Any

class Union(GeoAggregate):
    name: str
    output_field_class: Any
