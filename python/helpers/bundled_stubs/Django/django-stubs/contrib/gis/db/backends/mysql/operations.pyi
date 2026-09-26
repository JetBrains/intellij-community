from collections.abc import Callable
from typing import Any

from django.contrib.gis.db.backends.base.operations import BaseSpatialOperations
from django.contrib.gis.db.backends.utils import SpatialOperator
from django.contrib.gis.geos.geometry import GEOSGeometryBase
from django.db.backends.mysql.operations import DatabaseOperations
from django.utils.functional import cached_property
from typing_extensions import override

class MySQLOperations(BaseSpatialOperations, DatabaseOperations):
    name: str
    geom_func_prefix: str
    Adapter: Any
    @cached_property
    @override
    def mariadb(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def mysql(self) -> bool: ...  # type: ignore[override]
    @cached_property
    @override
    def select(self) -> str: ...  # type: ignore[override]
    @cached_property
    @override
    def from_text(self) -> str: ...  # type: ignore[override]
    @cached_property
    def collect(self) -> str: ...
    @cached_property
    def gis_operators(self) -> dict[str, SpatialOperator]: ...
    disallowed_aggregates: Any
    @cached_property
    @override
    def unsupported_functions(self) -> set[str]: ...  # type: ignore[override]
    @override
    def geo_db_type(self, f: Any) -> Any: ...
    @override
    def get_distance(self, f: Any, value: Any, lookup_type: Any) -> list[Any]: ...
    @override
    def get_geometry_converter(self, expression: Any) -> Callable[[Any, Any, Any], GEOSGeometryBase | None]: ...
