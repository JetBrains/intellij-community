from pathlib import Path
from typing import Any, Literal, TypedDict, type_check_only

import numpy as np
import shapely as shp

from ._typing import Array1D, Array2D, ReadPathOrBuffer

__gdal_version__: tuple[int, int, int]
__gdal_version_string__: str
__gdal_geos_version__: tuple[int, int, int] | None

@type_check_only
class _Capabilities(TypedDict):
    random_read: bool
    fast_set_next_by_index: bool
    fast_spatial_filter: bool
    fast_feature_count: bool
    fast_total_bounds: bool

@type_check_only
class _LayerInfo(TypedDict):
    layer_name: str
    # crs is `None` for non-spatial layers
    crs: str | None
    fields: Array1D[np.object_]  # field names (strings)
    dtypes: Array1D[np.object_]  # field dtypes (strings)
    ogr_types: list[str]
    ogr_subtypes: list[str]
    encoding: str
    fid_column: str
    geometry_name: str
    # geometry_type is `None` for non-spatial layers
    geometry_type: str | None
    features: int
    # total_bounds is `None` for non-spatial layers or if expensive to compute
    total_bounds: tuple[float, float, float, float] | None
    driver: str
    capabilities: _Capabilities
    dataset_metadata: dict[str, str] | None
    layer_metadata: dict[str, str] | None

@type_check_only
class _DriverDetails(TypedDict):
    long_name: str
    read: bool
    append: bool
    write: bool
    supports_vsi: bool
    help_topic_url: str | None
    extensions: list[str] | None

def list_drivers(read: bool = False, write: bool = False, append: bool = False) -> dict[str, Literal["r", "rw"]]: ...
def list_drivers_details() -> dict[str, _DriverDetails]: ...
def detect_write_driver(path: str | Path) -> str: ...  # `path` is coerced to string internally
def list_layers(path_or_buffer: ReadPathOrBuffer, /) -> Array2D[np.object_]: ...
def read_bounds(
    path_or_buffer: ReadPathOrBuffer,
    /,
    layer: int | str | None = None,
    skip_features: int = 0,
    max_features: int | None = None,
    where: str | None = None,
    bbox: tuple[float, float, float, float] | None = None,
    mask: shp.Geometry | None = None,
) -> tuple[Array1D[np.int64], Array2D[np.float64]]: ...
def read_info(
    path_or_buffer: ReadPathOrBuffer,
    /,
    layer: int | str | None = None,
    encoding: str | None = None,
    force_feature_count: bool = False,
    force_total_bounds: bool = False,
    **kwargs: Any,  # Dataset open options passed to OGR
) -> _LayerInfo: ...
def set_gdal_config_options(options: dict[str, Any]) -> None: ...
def get_gdal_config_option(name: str) -> Any: ...  # Could return str, int, bool, or None
def get_gdal_data_path() -> str: ...
def vsi_listtree(path: str | Path, pattern: str | None = None) -> list[str]: ...
def vsi_rmtree(path: str | Path) -> None: ...
def vsi_unlink(path: str | Path) -> None: ...
def vsi_curl_clear_cache(prefix: str | Path = "") -> None: ...
