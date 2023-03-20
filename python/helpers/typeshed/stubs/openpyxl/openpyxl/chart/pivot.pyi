from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class PivotSource(Serialisable):
    tagname: str
    name: Any
    fmtId: Any
    extLst: Any
    __elements__: Any
    def __init__(self, name: Any | None = ..., fmtId: Any | None = ..., extLst: Any | None = ...) -> None: ...

class PivotFormat(Serialisable):
    tagname: str
    idx: Any
    spPr: Any
    graphicalProperties: Any
    txPr: Any
    TextBody: Any
    marker: Any
    dLbl: Any
    DataLabel: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        idx: int = ...,
        spPr: Any | None = ...,
        txPr: Any | None = ...,
        marker: Any | None = ...,
        dLbl: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
