from typing import Any

from django.contrib.gis.db.backends.base.operations import BaseSpatialOperations
from django.contrib.gis.db.backends.utils import SpatialOperator
from django.db.backends.sqlite3.operations import DatabaseOperations
from django.utils.functional import cached_property
from typing_extensions import override

class SpatialiteNullCheckOperator(SpatialOperator): ...

class SpatiaLiteOperations(BaseSpatialOperations, DatabaseOperations):
    name: str
    spatialite: bool
    from_text: str  # type: ignore[assignment]
    Adapter: Any
    collect: str
    extent: str
    makeline: str
    unionagg: str
    gis_operators: Any
    disallowed_aggregates: Any
    select: str
    function_names: Any
    @cached_property
    @override
    def unsupported_functions(self) -> set[str]: ...  # type: ignore[override]
    @cached_property
    @override
    def spatial_version(self) -> Any: ...
    @override
    def convert_extent(self, box: Any) -> tuple[float, float, float, float] | None: ...  # type: ignore[override]
    @override
    def geo_db_type(self, f: Any) -> None: ...
    @override
    def get_distance(self, f: Any, value: Any, lookup_type: Any) -> Any: ...
    def geos_version(self) -> Any: ...
    def proj_version(self) -> Any: ...
    def lwgeom_version(self) -> Any: ...
    def rttopo_version(self) -> str | None: ...
    def geom_lib_version(self) -> str | None: ...
    def spatialite_version(self) -> Any: ...
    def spatialite_version_tuple(self) -> Any: ...
    @override
    def spatial_aggregate_name(self, agg_name: Any) -> Any: ...
    @override
    def geometry_columns(self) -> Any: ...
    @override
    def spatial_ref_sys(self) -> Any: ...
    @override
    def get_geometry_converter(self, expression: Any) -> Any: ...
