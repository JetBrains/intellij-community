from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import StubImageFile

def register_handler(handler) -> None: ...

class FITSStubImageFile(StubImageFile):
    format: ClassVar[Literal["FITS"]]
    format_description: ClassVar[str]
