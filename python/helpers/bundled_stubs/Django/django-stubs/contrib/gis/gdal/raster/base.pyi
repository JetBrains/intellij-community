from typing import Any

from django.contrib.gis.gdal.base import GDALBase

class GDALRasterBase(GDALBase):
    @property
    def metadata(self) -> Any: ...
    @metadata.setter
    def metadata(self, value: Any) -> None: ...
