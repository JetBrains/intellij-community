from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class TrendlineLabel(Serialisable):
    tagname: str
    layout: Any
    tx: Any
    numFmt: Any
    spPr: Any
    graphicalProperties: Any
    txPr: Any
    textProperties: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        layout: Any | None = ...,
        tx: Any | None = ...,
        numFmt: Any | None = ...,
        spPr: Any | None = ...,
        txPr: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class Trendline(Serialisable):
    tagname: str
    name: Any
    spPr: Any
    graphicalProperties: Any
    trendlineType: Any
    order: Any
    period: Any
    forward: Any
    backward: Any
    intercept: Any
    dispRSqr: Any
    dispEq: Any
    trendlineLbl: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        name: Any | None = ...,
        spPr: Any | None = ...,
        trendlineType: str = ...,
        order: Any | None = ...,
        period: Any | None = ...,
        forward: Any | None = ...,
        backward: Any | None = ...,
        intercept: Any | None = ...,
        dispRSqr: Any | None = ...,
        dispEq: Any | None = ...,
        trendlineLbl: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
