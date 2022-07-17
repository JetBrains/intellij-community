from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class LegendEntry(Serialisable):
    tagname: str
    idx: Any
    delete: Any
    txPr: Any
    extLst: Any
    __elements__: Any
    def __init__(self, idx: int = ..., delete: bool = ..., txPr: Any | None = ..., extLst: Any | None = ...) -> None: ...

class Legend(Serialisable):
    tagname: str
    legendPos: Any
    position: Any
    legendEntry: Any
    layout: Any
    overlay: Any
    spPr: Any
    graphicalProperties: Any
    txPr: Any
    textProperties: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        legendPos: str = ...,
        legendEntry=...,
        layout: Any | None = ...,
        overlay: Any | None = ...,
        spPr: Any | None = ...,
        txPr: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
