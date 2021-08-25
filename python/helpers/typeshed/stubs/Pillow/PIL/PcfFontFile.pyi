from typing import Any

from .FontFile import FontFile

PCF_MAGIC: int
PCF_PROPERTIES: Any
PCF_ACCELERATORS: Any
PCF_METRICS: Any
PCF_BITMAPS: Any
PCF_INK_METRICS: Any
PCF_BDF_ENCODINGS: Any
PCF_SWIDTHS: Any
PCF_GLYPH_NAMES: Any
PCF_BDF_ACCELERATORS: Any
BYTES_PER_ROW: Any

def sz(s, o): ...

class PcfFontFile(FontFile):
    name: str
    charset_encoding: Any
    toc: Any
    fp: Any
    info: Any
    def __init__(self, fp, charset_encoding: str = ...) -> None: ...
