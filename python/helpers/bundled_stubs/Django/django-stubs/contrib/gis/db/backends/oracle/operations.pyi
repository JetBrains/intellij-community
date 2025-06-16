from typing import Any

from django.contrib.gis.db.backends.base.operations import BaseSpatialOperations
from django.contrib.gis.db.backends.utils import SpatialOperator
from django.db.backends.oracle.operations import DatabaseOperations

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
    function_names: Any
    select: str
    gis_operators: Any
    unsupported_functions: Any
    def geo_quote_name(self, name: Any) -> Any: ...
    def geo_db_type(self, f: Any) -> Any: ...
    def get_distance(self, f: Any, value: Any, lookup_type: Any) -> Any: ...
    def get_geom_placeholder(self, f: Any, value: Any, compiler: Any) -> Any: ...
    def spatial_aggregate_name(self, agg_name: Any) -> Any: ...
    def geometry_columns(self) -> Any: ...
    def spatial_ref_sys(self) -> Any: ...
    def modify_insert_params(self, placeholder: Any, params: Any) -> Any: ...
    def get_geometry_converter(self, expression: Any) -> Any: ...
    def get_area_att_for_field(self, field: Any) -> Any: ...
