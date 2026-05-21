from collections.abc import MutableMapping
from typing import Any, Literal

from django.contrib.gis.db.backends.base.operations import BaseSpatialOperations
from django.contrib.gis.db.backends.utils import SpatialOperator
from django.contrib.gis.db.models.fields import GeometryField
from django.contrib.gis.db.models.lookups import GISLookup
from django.db.backends.postgresql.operations import DatabaseOperations
from django.db.models import Func
from django.utils.functional import cached_property
from typing_extensions import override

BILATERAL: Literal["bilateral"]

class PostGISOperator(SpatialOperator):
    geography: Any
    raster: bool | Literal["bilateral"]
    def __init__(self, geography: bool = False, raster: bool | Literal["bilateral"] = False, **kwargs: Any) -> None: ...
    def check_raster(self, lookup: Any, template_params: Any) -> Any: ...
    def check_geography(
        self,
        lookup: GISLookup,
        template_params: MutableMapping[str, Any],
    ) -> MutableMapping[str, Any]: ...

class ST_Polygon(Func):
    function: str
    def __init__(self, expr: Any) -> None: ...
    @cached_property
    @override
    def output_field(self) -> GeometryField: ...

class PostGISOperations(BaseSpatialOperations, DatabaseOperations):
    name: str
    postgis: bool
    geom_func_prefix: str
    Adapter: Any
    collect: Any
    extent: Any
    extent3d: Any
    length3d: Any
    makeline: Any
    perimeter3d: Any
    unionagg: Any
    gis_operators: Any
    unsupported_functions: Any
    select: str
    select_extent: Any
    @cached_property
    @override
    def function_names(self) -> Any: ...
    @cached_property
    @override
    def spatial_version(self) -> Any: ...
    @override
    def convert_extent(self, box: Any) -> tuple[float, float, float, float] | None: ...  # type: ignore[override]
    @override
    def convert_extent3d(self, box3d: Any) -> tuple[float, float, float, float, float, float] | None: ...  # type: ignore[override]
    @override
    def geo_db_type(self, f: Any) -> Any: ...
    @override
    def get_distance(self, f: Any, dist_val: Any, lookup_type: Any) -> Any: ...
    @override
    def get_geom_placeholder(self, f: Any, value: Any, compiler: Any) -> Any: ...
    def postgis_geos_version(self) -> Any: ...
    def postgis_lib_version(self) -> Any: ...
    def postgis_proj_version(self) -> Any: ...
    def postgis_version(self) -> Any: ...
    def postgis_full_version(self) -> Any: ...
    def postgis_version_tuple(self) -> Any: ...
    def proj_version_tuple(self) -> Any: ...
    @override
    def spatial_aggregate_name(self, agg_name: Any) -> Any: ...
    @override
    def geometry_columns(self) -> Any: ...
    @override
    def spatial_ref_sys(self) -> Any: ...
    def parse_raster(self, value: Any) -> Any: ...
    @override
    def distance_expr_for_lookup(self, lhs: Any, rhs: Any, **kwargs: Any) -> Any: ...
    @override
    def get_geometry_converter(self, expression: Any) -> Any: ...
    @override
    def get_area_att_for_field(self, field: Any) -> Any: ...
