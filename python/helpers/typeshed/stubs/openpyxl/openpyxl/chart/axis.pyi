from abc import abstractmethod
from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class ChartLines(Serialisable):
    tagname: str
    spPr: Any
    graphicalProperties: Any
    def __init__(self, spPr: Any | None = ...) -> None: ...

class Scaling(Serialisable):
    tagname: str
    logBase: Any
    orientation: Any
    max: Any
    min: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        logBase: Any | None = ...,
        orientation: str = ...,
        max: Any | None = ...,
        min: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class _BaseAxis(Serialisable):
    axId: Any
    scaling: Any
    delete: Any
    axPos: Any
    majorGridlines: Any
    minorGridlines: Any
    title: Any
    numFmt: Any
    number_format: Any
    majorTickMark: Any
    minorTickMark: Any
    tickLblPos: Any
    spPr: Any
    graphicalProperties: Any
    txPr: Any
    textProperties: Any
    crossAx: Any
    crosses: Any
    crossesAt: Any
    __elements__: Any
    def __init__(
        self,
        axId: Any | None = ...,
        scaling: Any | None = ...,
        delete: Any | None = ...,
        axPos: str = ...,
        majorGridlines: Any | None = ...,
        minorGridlines: Any | None = ...,
        title: Any | None = ...,
        numFmt: Any | None = ...,
        majorTickMark: Any | None = ...,
        minorTickMark: Any | None = ...,
        tickLblPos: Any | None = ...,
        spPr: Any | None = ...,
        txPr: Any | None = ...,
        crossAx: Any | None = ...,
        crosses: Any | None = ...,
        crossesAt: Any | None = ...,
    ) -> None: ...
    @property
    @abstractmethod
    def tagname(self) -> str: ...

class DisplayUnitsLabel(Serialisable):
    tagname: str
    layout: Any
    tx: Any
    text: Any
    spPr: Any
    graphicalProperties: Any
    txPr: Any
    textPropertes: Any
    __elements__: Any
    def __init__(
        self, layout: Any | None = ..., tx: Any | None = ..., spPr: Any | None = ..., txPr: Any | None = ...
    ) -> None: ...

class DisplayUnitsLabelList(Serialisable):
    tagname: str
    custUnit: Any
    builtInUnit: Any
    dispUnitsLbl: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self, custUnit: Any | None = ..., builtInUnit: Any | None = ..., dispUnitsLbl: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...

class NumericAxis(_BaseAxis):
    tagname: str
    axId: Any
    scaling: Any
    delete: Any
    axPos: Any
    majorGridlines: Any
    minorGridlines: Any
    title: Any
    numFmt: Any
    majorTickMark: Any
    minorTickMark: Any
    tickLblPos: Any
    spPr: Any
    txPr: Any
    crossAx: Any
    crosses: Any
    crossesAt: Any
    crossBetween: Any
    majorUnit: Any
    minorUnit: Any
    dispUnits: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        crossBetween: Any | None = ...,
        majorUnit: Any | None = ...,
        minorUnit: Any | None = ...,
        dispUnits: Any | None = ...,
        extLst: Any | None = ...,
        **kw,
    ) -> None: ...
    @classmethod
    def from_tree(cls, node): ...

class TextAxis(_BaseAxis):
    tagname: str
    axId: Any
    scaling: Any
    delete: Any
    axPos: Any
    majorGridlines: Any
    minorGridlines: Any
    title: Any
    numFmt: Any
    majorTickMark: Any
    minorTickMark: Any
    tickLblPos: Any
    spPr: Any
    txPr: Any
    crossAx: Any
    crosses: Any
    crossesAt: Any
    auto: Any
    lblAlgn: Any
    lblOffset: Any
    tickLblSkip: Any
    tickMarkSkip: Any
    noMultiLvlLbl: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        auto: Any | None = ...,
        lblAlgn: Any | None = ...,
        lblOffset: int = ...,
        tickLblSkip: Any | None = ...,
        tickMarkSkip: Any | None = ...,
        noMultiLvlLbl: Any | None = ...,
        extLst: Any | None = ...,
        **kw,
    ) -> None: ...

class DateAxis(TextAxis):
    tagname: str
    axId: Any
    scaling: Any
    delete: Any
    axPos: Any
    majorGridlines: Any
    minorGridlines: Any
    title: Any
    numFmt: Any
    majorTickMark: Any
    minorTickMark: Any
    tickLblPos: Any
    spPr: Any
    txPr: Any
    crossAx: Any
    crosses: Any
    crossesAt: Any
    auto: Any
    lblOffset: Any
    baseTimeUnit: Any
    majorUnit: Any
    majorTimeUnit: Any
    minorUnit: Any
    minorTimeUnit: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        auto: Any | None = ...,
        lblOffset: Any | None = ...,
        baseTimeUnit: Any | None = ...,
        majorUnit: Any | None = ...,
        majorTimeUnit: Any | None = ...,
        minorUnit: Any | None = ...,
        minorTimeUnit: Any | None = ...,
        extLst: Any | None = ...,
        **kw,
    ) -> None: ...

class SeriesAxis(_BaseAxis):
    tagname: str
    axId: Any
    scaling: Any
    delete: Any
    axPos: Any
    majorGridlines: Any
    minorGridlines: Any
    title: Any
    numFmt: Any
    majorTickMark: Any
    minorTickMark: Any
    tickLblPos: Any
    spPr: Any
    txPr: Any
    crossAx: Any
    crosses: Any
    crossesAt: Any
    tickLblSkip: Any
    tickMarkSkip: Any
    extLst: Any
    __elements__: Any
    def __init__(self, tickLblSkip: Any | None = ..., tickMarkSkip: Any | None = ..., extLst: Any | None = ..., **kw) -> None: ...
