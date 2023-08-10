from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

field: Any

class ImtImageFile(ImageFile):
    format: ClassVar[Literal["IMT"]]
    format_description: ClassVar[str]
