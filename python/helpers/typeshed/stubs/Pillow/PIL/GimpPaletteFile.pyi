from typing import Any

class GimpPaletteFile:
    rawmode: str
    palette: Any
    def __init__(self, fp) -> None: ...
    def getpalette(self): ...
