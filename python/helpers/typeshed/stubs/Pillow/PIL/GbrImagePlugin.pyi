from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

class GbrImageFile(ImageFile):
    format: ClassVar[Literal["GBR"]]
    format_description: ClassVar[str]
    im: Any
    def load(self) -> None: ...
