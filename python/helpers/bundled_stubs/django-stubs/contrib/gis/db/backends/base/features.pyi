from typing import Any

class BaseSpatialFeatures:
    gis_enabled: bool
    has_spatialrefsys_table: bool
    supports_add_srs_entry: bool
    supports_geometry_field_introspection: bool
    supports_3d_storage: bool
    supports_3d_functions: bool
    supports_transform: bool
    supports_null_geometries: bool
    supports_empty_geometries: bool
    supports_distance_geodetic: bool
    supports_length_geodetic: bool
    supports_perimeter_geodetic: bool
    supports_area_geodetic: bool
    supports_num_points_poly: bool
    supports_left_right_lookups: bool
    supports_dwithin_distance_expr: bool
    supports_raster: bool
    supports_geometry_field_unique_index: bool
    @property
    def supports_bbcontains_lookup(self) -> bool: ...
    @property
    def supports_contained_lookup(self) -> bool: ...
    @property
    def supports_crosses_lookup(self) -> bool: ...
    @property
    def supports_distances_lookups(self) -> bool: ...
    @property
    def supports_dwithin_lookup(self) -> bool: ...
    @property
    def supports_relate_lookup(self) -> bool: ...
    @property
    def supports_isvalid_lookup(self) -> bool: ...
    @property
    def supports_collect_aggr(self) -> bool: ...
    @property
    def supports_extent_aggr(self) -> bool: ...
    @property
    def supports_make_line_aggr(self) -> bool: ...
    @property
    def supports_union_aggr(self) -> bool: ...
    def __getattr__(self, name: Any) -> bool: ...
