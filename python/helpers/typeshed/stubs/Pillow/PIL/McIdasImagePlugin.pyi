from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

class McIdasImageFile(ImageFile):
    format: ClassVar[Literal["MCIDAS"]]
    format_description: ClassVar[str]
