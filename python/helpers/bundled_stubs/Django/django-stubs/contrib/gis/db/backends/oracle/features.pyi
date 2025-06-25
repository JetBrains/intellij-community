from typing import Any

from django.contrib.gis.db.backends.base.features import BaseSpatialFeatures
from django.db.backends.oracle.features import DatabaseFeatures as OracleDatabaseFeatures
from django.utils.functional import cached_property

class DatabaseFeatures(BaseSpatialFeatures, OracleDatabaseFeatures):
    supports_add_srs_entry: bool
    supports_geometry_field_introspection: bool
    supports_geometry_field_unique_index: bool
    supports_perimeter_geodetic: bool
    supports_dwithin_distance_expr: bool
    @cached_property
    def django_test_skips(self) -> dict[str, Any]: ...  # type: ignore[override]
