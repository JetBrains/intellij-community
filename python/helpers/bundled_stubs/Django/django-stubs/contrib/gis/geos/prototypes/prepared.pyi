from typing import Any

from django.contrib.gis.geos.libgeos import GEOSFuncFactory

geos_prepare: Any
prepared_destroy: Any

class PreparedPredicate(GEOSFuncFactory):
    argtypes: Any
    restype: Any
    errcheck: Any

prepared_contains: Any
prepared_contains_properly: Any
prepared_covers: Any
prepared_crosses: Any
prepared_disjoint: Any
prepared_intersects: Any
prepared_overlaps: Any
prepared_touches: Any
prepared_within: Any
