from _typeshed import Incomplete
from collections.abc import Sequence
from ctypes import _CVoidConstPLike
from typing_extensions import Literal, TypeAlias

from d3dshot.capture_output import CaptureOutput
from PIL import Image

# TODO: Complete types once we can import non-types dependencies
# See: https://github.com/python/typeshed/issues/5768
# from torch import Tensor
_Tensor: TypeAlias = Incomplete

class PytorchCaptureOutput(CaptureOutput):
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
    ) -> _Tensor: ...
    def to_pil(self, frame: _Tensor) -> Image.Image: ...
    def stack(self, frames: Sequence[_Tensor], stack_dimension: Literal["first", "last"]) -> _Tensor: ...
