from typing import Any

def env_func(f: Any, argtypes: Any) -> Any: ...
def pnt_func(f: Any) -> Any: ...
def topology_func(f: Any) -> Any: ...

from_json: Any
to_json: Any
to_kml: Any
getx: Any
gety: Any
getz: Any
from_wkb: Any
from_wkt: Any
from_gml: Any
create_geom: Any
clone_geom: Any
get_geom_ref: Any
get_boundary: Any
geom_convex_hull: Any
geom_diff: Any
geom_intersection: Any
geom_sym_diff: Any
geom_union: Any
add_geom: Any
import_wkt: Any
destroy_geom: Any
to_wkb: Any
to_wkt: Any
to_gml: Any
get_wkbsize: Any
assign_srs: Any
get_geom_srs: Any
get_area: Any
get_centroid: Any
get_dims: Any
get_coord_dim: Any
set_coord_dim: Any
is_empty: Any
get_geom_count: Any
get_geom_name: Any
get_geom_type: Any
get_point_count: Any
get_point: Any
geom_close_rings: Any
ogr_contains: Any
ogr_crosses: Any
ogr_disjoint: Any
ogr_equals: Any
ogr_intersects: Any
ogr_overlaps: Any
ogr_touches: Any
ogr_within: Any
geom_transform: Any
geom_transform_to: Any
get_envelope: Any
