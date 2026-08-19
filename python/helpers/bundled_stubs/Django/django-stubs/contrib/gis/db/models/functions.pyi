from typing import Any, ClassVar

from django.contrib.gis.db.models.fields import GeometryField
from django.contrib.gis.db.models.sql.conversion import AreaField, DistanceField
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import BinaryField, BooleanField, CharField, FloatField, Func, IntegerField, TextField
from django.db.models import Transform as StandardTransform
from django.db.models.expressions import BaseExpression
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from django.utils.functional import cached_property
from typing_extensions import override

NUMERIC_TYPES: Any

class GeoFuncMixin:
    function: str | None = None
    geom_param_pos: Any
    def __init__(self, *expressions: Any, **extra: Any) -> None: ...
    @property
    def name(self) -> str: ...
    @cached_property
    def geo_field(self) -> Any: ...
    # `as_sql` drops `template`/`arg_joiner` from `Func.as_sql`, matching runtime.
    def as_sql(
        self,
        compiler: SQLCompiler,
        connection: BaseDatabaseWrapper,
        function: str | None = None,
        **extra_context: Any,
    ) -> _AsSqlType: ...
    def resolve_expression(
        self,
        *args: Any,
        **kwargs: Any,
    ) -> Any: ...

class GeoFunc(GeoFuncMixin, Func): ...  # type: ignore[misc]

class GeomOutputGeoFunc(GeoFunc):
    @cached_property
    @override
    def output_field(self) -> GeometryField[Any, Any]: ...

class SQLiteDecimalToFloatMixin:
    def as_sqlite(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class OracleToleranceMixin:
    tolerance: float
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class Area(OracleToleranceMixin, GeoFunc):
    arity: int
    @cached_property
    @override
    def output_field(self) -> AreaField[Any, Any]: ...
    @override
    def as_sql(  # type: ignore[override]
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...
    @override
    def as_sqlite(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class Azimuth(GeoFunc):
    output_field: ClassVar[FloatField[Any, Any]]
    arity: int
    geom_param_pos: Any

class AsGeoJSON(GeoFunc):
    output_field: ClassVar[TextField[Any, Any]]
    def __init__(
        self, expression: Any, bbox: bool = False, crs: bool = False, precision: int = 8, **extra: Any
    ) -> None: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class AsGML(GeoFunc):
    geom_param_pos: Any
    output_field: ClassVar[TextField[Any, Any]]
    def __init__(self, expression: Any, version: int = 2, precision: int = 8, **extra: Any) -> None: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class AsKML(GeoFunc):
    output_field: ClassVar[TextField[Any, Any]]
    def __init__(self, expression: Any, precision: int = 8, **extra: Any) -> None: ...

class AsSVG(GeoFunc):
    output_field: ClassVar[TextField[Any, Any]]
    def __init__(self, expression: Any, relative: bool = False, precision: int = 8, **extra: Any) -> None: ...

class AsWKB(GeoFunc):
    output_field: ClassVar[BinaryField[Any, Any]]
    arity: int

class AsWKT(GeoFunc):
    output_field: ClassVar[TextField[Any, Any]]
    arity: int

class BoundingCircle(OracleToleranceMixin, GeomOutputGeoFunc):  # type: ignore[misc]
    def __init__(self, expression: Any, num_seg: int = 48, **extra: Any) -> None: ...
    @override
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class Centroid(OracleToleranceMixin, GeomOutputGeoFunc):  # type: ignore[misc]
    arity: int

class ClosestPoint(GeomOutputGeoFunc):
    arity: int
    geom_param_pos: tuple[int, int]

class Difference(OracleToleranceMixin, GeomOutputGeoFunc):  # type: ignore[misc]
    arity: int
    geom_param_pos: Any

class DistanceResultMixin:
    @cached_property
    def output_field(self) -> DistanceField[Any, Any]: ...
    def source_is_geography(self) -> Any: ...

class Distance(DistanceResultMixin, OracleToleranceMixin, GeoFunc):  # type: ignore[misc]
    geom_param_pos: Any
    spheroid: Any
    def __init__(self, expr1: Any, expr2: Any, spheroid: Any | None = None, **extra: Any) -> None: ...
    def as_postgresql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...
    @override
    def as_sqlite(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class Envelope(GeomOutputGeoFunc):
    arity: int

class ForcePolygonCW(GeomOutputGeoFunc):
    arity: int

class FromWKB(GeoFunc):
    arity: int
    def __init__(self, expression: BaseExpression, srid: int = 0, **extra: Any) -> None: ...
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class FromWKT(FromWKB): ...

class GeoHash(GeoFunc):
    output_field: ClassVar[TextField[Any, Any]]
    def __init__(self, expression: Any, precision: Any | None = None, **extra: Any) -> None: ...
    def as_mysql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class GeometryDistance(GeoFunc):
    output_field: ClassVar[FloatField[Any, Any]]
    arity: int
    function: str
    arg_joiner: str
    geom_param_pos: Any

class Intersection(OracleToleranceMixin, GeomOutputGeoFunc):  # type: ignore[misc]
    arity: int
    geom_param_pos: Any

class GeometryType(GeoFuncMixin, StandardTransform):  # type: ignore[misc]
    lookup_name: str
    output_field: ClassVar[CharField[Any, Any]]
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class IsEmpty(GeoFuncMixin, StandardTransform):  # type: ignore[misc]
    lookup_name: str
    output_field: ClassVar[BooleanField[Any, Any]]

class IsValid(OracleToleranceMixin, GeoFuncMixin, StandardTransform):  # type: ignore[misc]
    lookup_name: str
    output_field: ClassVar[BooleanField[Any, Any]]
    @override
    def as_oracle(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class Length(DistanceResultMixin, OracleToleranceMixin, GeoFunc):
    spheroid: Any
    def __init__(self, expr1: Any, spheroid: bool = True, **extra: Any) -> None: ...
    @override
    def as_sql(  # type: ignore[override]
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...
    def as_postgresql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...
    @override
    def as_sqlite(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class LineLocatePoint(GeoFunc):
    output_field: ClassVar[FloatField[Any, Any]]
    arity: int
    geom_param_pos: Any

class MakeValid(GeomOutputGeoFunc): ...

class MemSize(GeoFunc):
    output_field: ClassVar[IntegerField[Any, Any]]
    arity: int

class NumDimensions(GeoFuncMixin, StandardTransform):  # type: ignore[misc]
    lookup_name: str
    output_field: ClassVar[IntegerField[Any, Any]]
    arity: int

class NumGeometries(GeoFunc):
    output_field: ClassVar[IntegerField[Any, Any]]
    arity: int

class NumPoints(GeoFunc):
    output_field: ClassVar[IntegerField[Any, Any]]
    arity: int

class Perimeter(DistanceResultMixin, OracleToleranceMixin, GeoFunc):  # type: ignore[misc]
    arity: int
    def as_postgresql(
        self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any
    ) -> _AsSqlType: ...
    @override
    def as_sqlite(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class PointOnSurface(OracleToleranceMixin, GeomOutputGeoFunc):  # type: ignore[misc]
    arity: int

class Reverse(GeoFunc):
    arity: int

class Rotate(GeomOutputGeoFunc):
    def __init__(self, expression: Any, angle: Any, origin: Any | None = None, **extra: Any) -> None: ...

class Scale(SQLiteDecimalToFloatMixin, GeomOutputGeoFunc):  # type: ignore[misc]
    def __init__(self, expression: Any, x: Any, y: Any, z: float = 0.0, **extra: Any) -> None: ...

class SnapToGrid(SQLiteDecimalToFloatMixin, GeomOutputGeoFunc):  # type: ignore[misc]
    def __init__(self, expression: Any, *args: Any, **extra: Any) -> None: ...

class SymDifference(OracleToleranceMixin, GeomOutputGeoFunc):  # type: ignore[misc]
    arity: int
    geom_param_pos: Any

class Transform(GeomOutputGeoFunc):
    def __init__(self, expression: Any, srid: Any, **extra: Any) -> None: ...

class Translate(Scale):
    @override
    def as_sqlite(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper, **extra_context: Any) -> _AsSqlType: ...

class Union(OracleToleranceMixin, GeomOutputGeoFunc):  # type: ignore[misc]
    arity: int
    geom_param_pos: Any
