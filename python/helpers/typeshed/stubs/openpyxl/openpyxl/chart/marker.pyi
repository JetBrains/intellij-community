from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Marker(Serialisable):
    tagname: str
    symbol: Any
    size: Any
    spPr: Any
    graphicalProperties: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self, symbol: Any | None = ..., size: Any | None = ..., spPr: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...

class DataPoint(Serialisable):
    tagname: str
    idx: Any
    invertIfNegative: Any
    marker: Any
    bubble3D: Any
    explosion: Any
    spPr: Any
    graphicalProperties: Any
    pictureOptions: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        idx: Any | None = ...,
        invertIfNegative: Any | None = ...,
        marker: Any | None = ...,
        bubble3D: Any | None = ...,
        explosion: Any | None = ...,
        spPr: Any | None = ...,
        pictureOptions: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
