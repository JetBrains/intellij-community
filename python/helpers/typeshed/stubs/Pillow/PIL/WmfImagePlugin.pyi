from typing import Any

from .ImageFile import StubImageFile

def register_handler(handler) -> None: ...

class WmfHandler:
    bbox: Any
    def open(self, im) -> None: ...
    def load(self, im): ...

class WmfStubImageFile(StubImageFile):
    format: str
    format_description: str
    def load(self, dpi: Any | None = ...) -> None: ...
