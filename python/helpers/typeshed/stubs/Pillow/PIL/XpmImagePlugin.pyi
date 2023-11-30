from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

xpm_head: Incomplete

class XpmImageFile(ImageFile):
    format: ClassVar[Literal["XPM"]]
    format_description: ClassVar[str]
    def load_read(self, bytes): ...
