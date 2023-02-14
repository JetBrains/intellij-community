from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import StubImageFile

def register_handler(handler) -> None: ...

class WmfHandler:
    bbox: Any
    def open(self, im) -> None: ...
    def load(self, im): ...

class WmfStubImageFile(StubImageFile):
    format: ClassVar[Literal["WMF"]]
    format_description: ClassVar[str]
    def load(self, dpi: Any | None = ...) -> None: ...
