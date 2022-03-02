from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

MODES: Any

class PsdImageFile(ImageFile):
    format: ClassVar[Literal["PSD"]]
    format_description: ClassVar[str]
    mode: Any
    tile: Any
    frame: Any
    fp: Any
    def seek(self, layer): ...
    def tell(self): ...
    im: Any
    def load_prepare(self) -> None: ...
