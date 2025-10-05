from typing import Any

from django.contrib.gis.geos.libgeos import GEOSFuncFactory

class DblFromGeom(GEOSFuncFactory):
    restype: Any
    errcheck: Any

geos_area: Any
geos_distance: Any
geos_length: Any
geos_isvalidreason: Any

__all__ = ["geos_area", "geos_distance", "geos_length", "geos_isvalidreason"]
