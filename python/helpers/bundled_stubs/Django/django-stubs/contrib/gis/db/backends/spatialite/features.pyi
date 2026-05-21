from django.contrib.gis.db.backends.base.features import BaseSpatialFeatures
from django.db.backends.sqlite3.features import DatabaseFeatures as SQLiteDatabaseFeatures
from django.utils.functional import cached_property
from typing_extensions import override

class DatabaseFeatures(BaseSpatialFeatures, SQLiteDatabaseFeatures):
    supports_3d_storage: bool
    @cached_property
    @override
    def supports_area_geodetic(self) -> bool: ...  # type: ignore[override]
