from typing import Any

from .FontFile import FontFile

bdf_slant: Any
bdf_spacing: Any

def bdf_char(f): ...

class BdfFontFile(FontFile):
    def __init__(self, fp) -> None: ...
