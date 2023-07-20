import enum
from _typeshed import Incomplete
from collections.abc import Sequence
from ctypes import _CVoidConstPLike
from typing_extensions import Literal, TypeAlias

from PIL import Image

_Frame: TypeAlias = Image.Image | Incomplete
# TODO: Complete types once we can import non-types dependencies
# See: #5768
# from torch import Tensor
# from comtypes import IUnknown
# import numpy.typing as npt
# _Frame: TypeAlias = Image.Image | npt.NDArray[np.int32] | npt.NDArray[np.float32] | Tensor

class CaptureOutputs(enum.Enum):
    PIL: int
    NUMPY: int
    NUMPY_FLOAT: int
    PYTORCH: int
    PYTORCH_FLOAT: int
    PYTORCH_GPU: int
    PYTORCH_FLOAT_GPU: int

class CaptureOutputError(BaseException): ...

# All CaptureOutput methods just reference the backend. Making this both a base class and a wrapper.
class CaptureOutput:
    # `backend` is a subclass of CaptureOutput based on the CaptureOutputs enum passed to __init__
    backend: CaptureOutput
    def __init__(self, backend: CaptureOutputs = ...) -> None: ...
    def process(
        self,
        pointer: _CVoidConstPLike,
        pitch: int,
        size: int,
        width: int,
        height: int,
        region: tuple[int, int, int, int],
        rotation: int,
    ) -> _Frame: ...
    def to_pil(self, frame: _Frame) -> Image.Image: ...
    def stack(self, frames: Sequence[_Frame], stack_dimension: Literal["first", "last"]) -> _Frame: ...
