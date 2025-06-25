from typing import Any, ClassVar

from django.contrib.gis.db.backends.base.models import SpatialRefSysMixin
from django.db import models
from typing_extensions import Self

class SpatialiteGeometryColumns(models.Model):
    f_table_name: Any
    f_geometry_column: Any
    coord_dimension: Any
    srid: Any
    spatial_index_enabled: Any
    type: Any
    objects: ClassVar[models.Manager[Self]]

    @classmethod
    def table_name_col(cls) -> Any: ...
    @classmethod
    def geom_col_name(cls) -> Any: ...

class SpatialiteSpatialRefSys(models.Model, SpatialRefSysMixin):
    srid: Any
    auth_name: Any
    auth_srid: Any
    ref_sys_name: Any
    proj4text: Any
    srtext: Any
    objects: ClassVar[models.Manager[Self]]

    @property
    def wkt(self) -> Any: ...
