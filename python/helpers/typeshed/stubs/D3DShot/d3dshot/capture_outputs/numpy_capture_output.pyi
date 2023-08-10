from _typeshed import Incomplete
from collections.abc import Sequence
from ctypes import _CVoidConstPLike
from typing_extensions import Literal, TypeAlias

from d3dshot.capture_output import CaptureOutput
from PIL import Image

# TODO: Complete types once we can import non-types dependencies
# See: #5768
# import numpy as np
# import numpy.typing as npt
# _NDArray: TypeAlias = npt.NDArray[np.int32]
_NDArray: TypeAlias = Incomplete

class NumpyCaptureOutput(CaptureOutput):
    def __init__(self) -> None: ...
    def process(
        self,
        pointer: _CVoidConstPLike,
        pitch: int,
        size: int,
        width: int,
        height: int,
        region: tuple[int, int, int, int],
        rotation: int,
    ) -> _NDArray: ...
    def to_pil(self, frame: _NDArray) -> Image.Image: ...
    def stack(self, frames: Sequence[_NDArray] | _NDArray, stack_dimension: Literal["first", "last"]) -> _NDArray: ...
