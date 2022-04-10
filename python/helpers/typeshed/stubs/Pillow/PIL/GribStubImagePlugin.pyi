from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import StubImageFile

def register_handler(handler) -> None: ...

class GribStubImageFile(StubImageFile):
    format: ClassVar[Literal["GRIB"]]
    format_description: ClassVar[str]
