from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal, TypeAlias

from ._imaging import _PixelAccessor
from .ImageFile import ImageFile

SUPPORTED: bool
_XMP_Tags: TypeAlias = dict[str, str | _XMP_Tags]

class WebPImageFile(ImageFile):
    format: ClassVar[Literal["WEBP"]]
    format_description: ClassVar[str]
    def getxmp(self) -> _XMP_Tags: ...
    def seek(self, frame) -> None: ...
    fp: Incomplete
    tile: Incomplete
    def load(self) -> _PixelAccessor: ...
    def tell(self): ...
