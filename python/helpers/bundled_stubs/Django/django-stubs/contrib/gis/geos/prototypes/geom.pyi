from ctypes import c_char_p
from typing import Any

from django.contrib.gis.geos.libgeos import GEOSFuncFactory

c_uchar_p: Any

class geos_char_p(c_char_p): ...

class GeomOutput(GEOSFuncFactory):
    restype: Any
    errcheck: Any

class IntFromGeom(GEOSFuncFactory):
    argtypes: Any
    restype: Any
    errcheck: Any

class StringFromGeom(GEOSFuncFactory):
    argtypes: Any
    restype: Any
    errcheck: Any

geos_normalize: Any
geos_type: Any
geos_typeid: Any
get_dims: Any
get_num_coords: Any
get_num_geoms: Any
create_point: Any
create_linestring: Any
create_linearring: Any
create_polygon: Any
create_empty_polygon: Any
create_collection: Any
get_extring: Any
get_intring: Any
get_nrings: Any
get_geomn: Any
geom_clone: Any
destroy_geom: Any
geos_get_srid: Any
geos_set_srid: Any
