from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

class PcdImageFile(ImageFile):
    format: ClassVar[Literal["PCD"]]
    format_description: ClassVar[str]
    im: Any
    def load_end(self) -> None: ...
