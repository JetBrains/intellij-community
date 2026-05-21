from typing import Any

from django.contrib.gis.db.backends.base.operations import BaseSpatialOperations
from django.contrib.gis.db.backends.utils import SpatialOperator
from django.db.backends.oracle.operations import DatabaseOperations
from typing_extensions import override

DEFAULT_TOLERANCE: str

class SDOOperator(SpatialOperator):
    sql_template: str

class SDODWithin(SpatialOperator):
    sql_template: str

class SDODisjoint(SpatialOperator):
    sql_template: Any

class SDORelate(SpatialOperator):
    sql_template: str
    def check_relate_argument(self, arg: Any) -> None: ...

class OracleOperations(BaseSpatialOperations, DatabaseOperations):
    name: str
    oracle: bool
    disallowed_aggregates: Any
    Adapter: Any
    extent: str
    unionagg: str
    from_text: str  # type: ignore[assignment]
    function_names: Any
    select: str
    gis_operators: Any
    unsupported_functions: Any
    @override
    def geo_quote_name(self, name: Any) -> Any: ...
    @override
    def convert_extent(self, clob: Any) -> tuple[float, float, float, float] | None: ...  # type: ignore[override]
    @override
    def geo_db_type(self, f: Any) -> Any: ...
    @override
    def get_distance(self, f: Any, value: Any, lookup_type: Any) -> Any: ...
    @override
    def get_geom_placeholder(self, f: Any, value: Any, compiler: Any) -> Any: ...
    @override
    def spatial_aggregate_name(self, agg_name: Any) -> Any: ...
    @override
    def geometry_columns(self) -> Any: ...
    @override
    def spatial_ref_sys(self) -> Any: ...
    @override
    def modify_insert_params(self, placeholder: Any, params: Any) -> Any: ...
    @override
    def get_geometry_converter(self, expression: Any) -> Any: ...
    @override
    def get_area_att_for_field(self, field: Any) -> Any: ...
