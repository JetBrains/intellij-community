from collections.abc import Callable
from typing import Literal, overload

import numpy as np
from numpy.typing import NDArray

from ._typing import ArrayLikeSeq, GeoArray, GeoT, OptGeoArrayLike, OptGeoArrayLikeSeq, OptGeoT

__all__ = ["transform", "count_coordinates", "get_coordinates", "set_coordinates"]

@overload
def transform(
    geometry: OptGeoT, transformation: Callable[[NDArray[np.float64]], NDArray[np.float64]], include_z: bool = False
) -> OptGeoT: ...
@overload
def transform(
    geometry: OptGeoArrayLikeSeq, transformation: Callable[[NDArray[np.float64]], NDArray[np.float64]], include_z: bool = False
) -> GeoArray: ...
def count_coordinates(geometry: OptGeoArrayLike) -> int: ...
@overload
def get_coordinates(
    geometry: OptGeoArrayLike, include_z: bool = False, return_index: Literal[False] = False
) -> NDArray[np.float64]: ...
@overload
def get_coordinates(
    geometry: OptGeoArrayLike, include_z: bool = False, *, return_index: Literal[True]
) -> tuple[NDArray[np.float64], NDArray[np.int64]]: ...
@overload
def get_coordinates(
    geometry: OptGeoArrayLike, include_z: bool, return_index: Literal[True]
) -> tuple[NDArray[np.float64], NDArray[np.int64]]: ...
@overload
def get_coordinates(
    geometry: OptGeoArrayLike, include_z: bool = False, *, return_index: bool
) -> NDArray[np.float64] | tuple[NDArray[np.float64], NDArray[np.int64]]: ...
@overload
def get_coordinates(
    geometry: OptGeoArrayLike, include_z: bool, return_index: bool
) -> NDArray[np.float64] | tuple[NDArray[np.float64], NDArray[np.int64]]: ...
@overload
def set_coordinates(geometry: GeoT, coordinates: ArrayLikeSeq[float]) -> GeoT: ...
@overload
def set_coordinates(geometry: OptGeoArrayLikeSeq, coordinates: ArrayLikeSeq[float]) -> GeoArray: ...
