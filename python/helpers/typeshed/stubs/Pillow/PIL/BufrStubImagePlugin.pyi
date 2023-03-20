from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import StubImageFile

def register_handler(handler) -> None: ...

class BufrStubImageFile(StubImageFile):
    format: ClassVar[Literal["BUFR"]]
    format_description: ClassVar[str]
