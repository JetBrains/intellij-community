from typing import Any

from numpy.ma import MaskedArray
from numpy.typing import NDArray

def fillnodata(
    image: NDArray[Any] | MaskedArray[Any, Any],
    mask: NDArray[Any] | None = None,
    max_search_distance: float = 100.0,
    smoothing_iterations: int = 0,
) -> NDArray[Any]: ...
