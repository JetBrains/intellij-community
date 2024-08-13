from typing import Any

from django.contrib.gis.db.backends.base.features import BaseSpatialFeatures
from django.db.backends.mysql.features import DatabaseFeatures as MySQLDatabaseFeatures
from django.utils.functional import cached_property

class DatabaseFeatures(BaseSpatialFeatures, MySQLDatabaseFeatures):
    has_spatialrefsys_table: bool
    supports_add_srs_entry: bool
    supports_distance_geodetic: bool
    supports_length_geodetic: bool
    supports_area_geodetic: bool
    supports_transform: bool
    supports_null_geometries: bool
    supports_num_points_poly: bool
    @property
    def empty_intersection_returns_none(self) -> bool: ...
    @cached_property
    def supports_geometry_field_unique_index(self) -> bool: ...  # type: ignore[override]
    @cached_property
    def django_test_skips(self) -> dict[str, Any]: ...  # type: ignore[override]
