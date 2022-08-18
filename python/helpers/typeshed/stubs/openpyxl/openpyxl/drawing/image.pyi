from typing import Any

class Image:
    anchor: str
    ref: Any
    format: Any
    def __init__(self, img) -> None: ...
    @property
    def path(self): ...
