import os
from collections.abc import Collection, Mapping
from typing import Any, Literal, overload

import geopandas as gpd
import pandas as pd
import shapely as shp

from ._typing import ArrayLikeInt, ReadPathOrBuffer, WritePathOrBuffer

@overload
def read_dataframe(
    path_or_buffer: ReadPathOrBuffer | os.PathLike[str],
    /,
    layer: int | str | None = None,
    encoding: str | None = None,
    columns: Collection[str] | None = None,
    read_geometry: Literal[True] = True,
    force_2d: bool = False,
    skip_features: int = 0,
    max_features: int | None = None,
    where: str | None = None,
    bbox: tuple[float, float, float, float] | None = None,
    mask: shp.Geometry | None = None,
    fids: ArrayLikeInt | None = None,
    sql: str | None = None,
    sql_dialect: str | None = None,
    fid_as_index: bool = False,
    use_arrow: bool | None = None,
    on_invalid: Literal["raise", "warn", "ignore", "fix"] = "raise",
    arrow_to_pandas_kwargs: Mapping[str, Any] | None = None,
    datetime_as_string: bool = False,
    mixed_offsets_as_utc: bool = True,
    **kwargs: Any,  # Dataset open options passed to OGR
) -> gpd.GeoDataFrame: ...
@overload
def read_dataframe(
    path_or_buffer: ReadPathOrBuffer | os.PathLike[str],
    /,
    layer: int | str | None = None,
    encoding: str | None = None,
    columns: Collection[str] | None = None,
    *,
    read_geometry: Literal[False],
    force_2d: bool = False,
    skip_features: int = 0,
    max_features: int | None = None,
    where: str | None = None,
    bbox: tuple[float, float, float, float] | None = None,
    mask: shp.Geometry | None = None,
    fids: ArrayLikeInt | None = None,
    sql: str | None = None,
    sql_dialect: str | None = None,
    fid_as_index: bool = False,
    use_arrow: bool | None = None,
    on_invalid: Literal["raise", "warn", "ignore", "fix"] = "raise",
    arrow_to_pandas_kwargs: Mapping[str, Any] | None = None,
    datetime_as_string: bool = False,
    mixed_offsets_as_utc: bool = True,
    **kwargs: Any,  # Dataset open options passed to OGR
) -> pd.DataFrame: ...

def write_dataframe(
    df: pd.DataFrame,
    path: WritePathOrBuffer,
    layer: str | None = None,
    driver: str | None = None,
    encoding: str | None = None,
    geometry_type: str | None = None,
    promote_to_multi: bool | None = None,
    nan_as_null: bool = True,
    append: bool = False,
    use_arrow: bool | None = None,
    dataset_metadata: dict[str, Any] | None = None,
    layer_metadata: dict[str, Any] | None = None,
    metadata: dict[str, Any] | None = None,
    dataset_options: dict[str, Any] | None = None,
    layer_options: dict[str, Any] | None = None,
    **kwargs: Any,  # Additional driver-specific dataset or layer creation options passed to OGR
) -> None: ...
