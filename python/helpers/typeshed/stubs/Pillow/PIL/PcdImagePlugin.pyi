from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

class PcdImageFile(ImageFile):
    format: ClassVar[Literal["PCD"]]
    format_description: ClassVar[str]
    im: Incomplete
    def load_end(self) -> None: ...
