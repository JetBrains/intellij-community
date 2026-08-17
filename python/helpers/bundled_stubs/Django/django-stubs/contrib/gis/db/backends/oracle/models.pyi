from typing import Any, ClassVar, Self

from django.contrib.gis.db import models
from django.contrib.gis.db.backends.base.models import SpatialRefSysMixin
from django.db.models.manager import Manager

class OracleGeometryColumns(models.Model):
    table_name: Any
    column_name: Any
    srid: Any
    objects: ClassVar[Manager[Self]]

    @classmethod
    def table_name_col(cls) -> Any: ...
    @classmethod
    def geom_col_name(cls) -> Any: ...

class OracleSpatialRefSys(models.Model, SpatialRefSysMixin):
    cs_name: Any
    srid: Any
    auth_srid: Any
    auth_name: Any
    wktext: Any
    cs_bounds: Any
    objects: ClassVar[Manager[Self]]

    @property
    def wkt(self) -> Any: ...
