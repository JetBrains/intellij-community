from collections.abc import Collection, Iterable
from contextlib import AbstractContextManager
from typing import Any, Literal, TypedDict, overload, type_check_only

import numpy as np
import pyarrow as pa  # type: ignore[import-not-found]  # pyright: ignore[reportMissingImports]
import shapely as shp

from ._typing import Array1D, ArrayLikeInt, ReadPathOrBuffer, SupportsArrowCStream, WritePathOrBuffer

DRIVERS_NO_MIXED_SINGLE_MULTI: set[str]
DRIVERS_NO_MIXED_DIMENSIONS: set[str]

@type_check_only
class _Meta(TypedDict):
    crs: str
    fields: Array1D[np.object_]
    dtypes: Array1D[np.object_]
    ogr_types: list[str]
    ogr_subtypes: list[str]
    encoding: str
    geometry_type: str
    geometry_name: str
    fid_column: str

@overload
def read(
    path_or_buffer: ReadPathOrBuffer,
    /,
    layer: int | str | None = None,
    encoding: str | None = None,
    columns: Collection[str] | None = None,
    read_geometry: bool = True,
    force_2d: bool = False,
    skip_features: int = 0,
    max_features: int | None = None,
    where: str | None = None,
    bbox: tuple[float, float, float, float] | None = None,
    mask: shp.Geometry | None = None,
    fids: ArrayLikeInt | None = None,
    sql: str | None = None,
    sql_dialect: str | None = None,
    return_fids: Literal[False] = False,
    datetime_as_string: bool = False,
    **kwargs: Any,  # Dataset open options passed to OGR
) -> tuple[_Meta, None, Array1D[np.object_] | None, list[np.ndarray]]: ...
@overload
def read(
    path_or_buffer: ReadPathOrBuffer,
    /,
    layer: int | str | None = None,
    encoding: str | None = None,
    columns: Collection[str] | None = None,
    read_geometry: bool = True,
    force_2d: bool = False,
    skip_features: int = 0,
    max_features: int | None = None,
    where: str | None = None,
    bbox: tuple[float, float, float, float] | None = None,
    mask: shp.Geometry | None = None,
    fids: ArrayLikeInt | None = None,
    sql: str | None = None,
    sql_dialect: str | None = None,
    *,
    return_fids: Literal[True],
    datetime_as_string: bool = False,
    **kwargs: Any,  # Dataset open options passed to OGR
) -> tuple[_Meta, Array1D[np.int64], Array1D[np.object_] | None, list[np.ndarray]]: ...

def read_arrow(
    path_or_buffer: ReadPathOrBuffer,
    /,
    layer: int | str | None = None,
    encoding: str | None = None,
    columns: Collection[str] | None = None,
    read_geometry: bool = True,
    force_2d: bool = False,
    skip_features: int = 0,
    max_features: int | None = None,
    where: str | None = None,
    bbox: tuple[float, float, float, float] | None = None,
    mask: shp.Geometry | None = None,
    fids: ArrayLikeInt | None = None,
    sql: str | None = None,
    sql_dialect: str | None = None,
    return_fids: bool = False,
    datetime_as_string: bool = False,
    *,
    batch_size: int = 65536,  # Extracted from kwargs
    **kwargs: Any,  # Dataset open options passed to OGR
) -> tuple[_Meta, pa.Table]: ...

@overload
def open_arrow(
    path_or_buffer: ReadPathOrBuffer,
    /,
    layer: int | str | None = None,
    encoding: str | None = None,
    columns: Collection[str] | None = None,
    read_geometry: bool = True,
    force_2d: bool = False,
    skip_features: int = 0,
    max_features: int | None = None,
    where: str | None = None,
    bbox: tuple[float, float, float, float] | None = None,
    mask: shp.Geometry | None = None,
    fids: ArrayLikeInt | None = None,
    sql: str | None = None,
    sql_dialect: str | None = None,
    return_fids: bool = False,
    batch_size: int = 65536,
    use_pyarrow: Literal[False] = False,
    datetime_as_string: bool = False,
    **kwargs: Any,  # Dataset open options passed to OGR
) -> AbstractContextManager[tuple[_Meta, SupportsArrowCStream]]: ...
@overload
def open_arrow(
    path_or_buffer: ReadPathOrBuffer,
    /,
    layer: int | str | None = None,
    encoding: str | None = None,
    columns: Collection[str] | None = None,
    read_geometry: bool = True,
    force_2d: bool = False,
    skip_features: int = 0,
    max_features: int | None = None,
    where: str | None = None,
    bbox: tuple[float, float, float, float] | None = None,
    mask: shp.Geometry | None = None,
    fids: ArrayLikeInt | None = None,
    sql: str | None = None,
    sql_dialect: str | None = None,
    return_fids: bool = False,
    batch_size: int = 65536,
    *,
    use_pyarrow: Literal[True],
    datetime_as_string: bool = False,
    **kwargs: Any,  # Dataset open options passed to OGR
) -> AbstractContextManager[tuple[_Meta, pa.RecordBatchReader]]: ...

def write(
    path: WritePathOrBuffer,
    geometry: np.ndarray | None,  # ndarray of WKB encoded geometries or None
    field_data: Iterable[np.ndarray] | None,
    fields: Iterable[str],
    field_mask: Iterable[np.ndarray | None] | None = None,
    layer: str | None = None,
    driver: str | None = None,
    geometry_type: str | None = None,
    crs: str | None = None,
    encoding: str | None = None,
    promote_to_multi: bool | None = None,
    nan_as_null: bool = True,
    append: bool = False,
    dataset_metadata: dict[str, str] | None = None,
    layer_metadata: dict[str, str] | None = None,
    metadata: dict[str, str] | None = None,
    dataset_options: dict[str, Any] | None = None,
    layer_options: dict[str, Any] | None = None,
    gdal_tz_offsets: dict[str, Any] | None = None,
    **kwargs: Any,  # Additional driver-specific dataset or layer creation options passed to OGR
) -> None: ...
def write_arrow(
    arrow_obj: SupportsArrowCStream,
    path: WritePathOrBuffer,
    layer: str | None = None,
    driver: str | None = None,
    geometry_name: str | None = None,
    geometry_type: str | None = None,
    crs: str | None = None,
    encoding: str | None = None,
    append: bool = False,
    dataset_metadata: dict[str, str] | None = None,
    layer_metadata: dict[str, str] | None = None,
    metadata: dict[str, str] | None = None,
    dataset_options: dict[str, Any] | None = None,
    layer_options: dict[str, Any] | None = None,
    **kwargs: Any,  # Additional driver-specific dataset or layer creation options passed to OGR
) -> None: ...
