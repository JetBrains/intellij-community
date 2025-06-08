from django.contrib.gis.db.backends.base.features import BaseSpatialFeatures
from django.db.backends.oracle.features import DatabaseFeatures as OracleDatabaseFeatures

class DatabaseFeatures(BaseSpatialFeatures, OracleDatabaseFeatures):
    supports_add_srs_entry: bool
    supports_geometry_field_introspection: bool
    supports_geometry_field_unique_index: bool
    supports_perimeter_geodetic: bool
    supports_dwithin_distance_expr: bool
