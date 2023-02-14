from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

b_whitespace: bytes
MODES: Any

class PpmImageFile(ImageFile):
    format: ClassVar[Literal["PPM"]]
    format_description: ClassVar[str]
