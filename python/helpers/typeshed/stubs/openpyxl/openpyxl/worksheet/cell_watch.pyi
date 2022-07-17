from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class CellWatch(Serialisable):
    tagname: str
    r: Any
    def __init__(self, r: Any | None = ...) -> None: ...

class CellWatches(Serialisable):
    tagname: str
    cellWatch: Any
    __elements__: Any
    def __init__(self, cellWatch=...) -> None: ...
