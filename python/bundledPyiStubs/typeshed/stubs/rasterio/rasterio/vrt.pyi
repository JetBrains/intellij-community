from types import TracebackType
from typing_extensions import Self

from rasterio._warp import WarpedVRTReaderBase
from rasterio.transform import TransformMethodsMixin
from rasterio.windows import WindowMethodsMixin

class WarpedVRT(WarpedVRTReaderBase, WindowMethodsMixin, TransformMethodsMixin):
    def __enter__(self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_val: BaseException | None, exc_tb: TracebackType | None
    ) -> None: ...
    def __del__(self) -> None: ...
