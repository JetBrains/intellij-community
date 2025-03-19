from typing import overload

import numpy as np
from numpy.typing import NDArray

from .._typing import ArrayLike, ArrayLikeSeq
from ..lib import Geometry
from ..prepared import PreparedGeometry

@overload
def contains(geometry: Geometry | PreparedGeometry[Geometry], x: float, y: float) -> bool: ...
@overload
def contains(
    geometry: Geometry | PreparedGeometry[Geometry], x: ArrayLikeSeq[float], y: ArrayLike[float]
) -> NDArray[np.bool_]: ...
@overload
def contains(
    geometry: Geometry | PreparedGeometry[Geometry], x: ArrayLike[float], y: ArrayLikeSeq[float]
) -> NDArray[np.bool_]: ...
@overload
def contains(
    geometry: Geometry | PreparedGeometry[Geometry], x: ArrayLike[float], y: ArrayLike[float]
) -> bool | NDArray[np.bool_]: ...
@overload
def touches(geometry: Geometry | PreparedGeometry[Geometry], x: float, y: float) -> bool: ...
@overload
def touches(
    geometry: Geometry | PreparedGeometry[Geometry], x: ArrayLikeSeq[float], y: ArrayLike[float]
) -> NDArray[np.bool_]: ...
@overload
def touches(
    geometry: Geometry | PreparedGeometry[Geometry], x: ArrayLike[float], y: ArrayLikeSeq[float]
) -> NDArray[np.bool_]: ...
@overload
def touches(
    geometry: Geometry | PreparedGeometry[Geometry], x: ArrayLike[float], y: ArrayLike[float]
) -> bool | NDArray[np.bool_]: ...
