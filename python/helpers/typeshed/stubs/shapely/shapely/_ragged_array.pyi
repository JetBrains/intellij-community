import numpy as np
from numpy.typing import NDArray

from ._geometry import GeometryType
from ._typing import ArrayLike, ArrayLikeSeq, GeoArray, OptGeoArrayLikeSeq

def to_ragged_array(
    geometries: OptGeoArrayLikeSeq, include_z: bool | None = None
) -> tuple[GeometryType, NDArray[np.float64], tuple[NDArray[np.int64], ...]]: ...
def from_ragged_array(
    geometry_type: GeometryType, coords: ArrayLike[float], offsets: ArrayLikeSeq[int] | None = None
) -> GeoArray: ...
