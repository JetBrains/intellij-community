from typing import Any

from django.contrib.gis.geos.libgeos import GEOSFuncFactory

class Topology(GEOSFuncFactory):
    argtypes: Any
    restype: Any
    errcheck: Any

geos_boundary: Any
geos_buffer: Any
geos_bufferwithstyle: Any
geos_centroid: Any
geos_convexhull: Any
geos_difference: Any
geos_envelope: Any
geos_intersection: Any
geos_linemerge: Any
geos_pointonsurface: Any
geos_preservesimplify: Any
geos_simplify: Any
geos_symdifference: Any
geos_union: Any
geos_unary_union: Any
geos_relate: Any
geos_project: Any
geos_interpolate: Any
geos_project_normalized: Any
geos_interpolate_normalized: Any
