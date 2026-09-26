from django.contrib.gis.db.backends.base.features import BaseSpatialFeatures
from django.db.backends.oracle.features import DatabaseFeatures as OracleDatabaseFeatures
from django.utils.functional import cached_property
from typing_extensions import override

class DatabaseFeatures(BaseSpatialFeatures, OracleDatabaseFeatures):
    supports_add_srs_entry: bool
    supports_geometry_field_introspection: bool
    supports_geometry_field_unique_index: bool
    supports_perimeter_geodetic: bool
    supports_dwithin_distance_expr: bool
    supports_tolerance_parameter: bool

    @cached_property
    @override
    def django_test_skips(self) -> dict[str, set[str]]: ...  # type: ignore[override]
