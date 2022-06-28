from abc import abstractmethod
from typing import Any

from openpyxl.descriptors.serialisable import Serialisable as Serialisable

class _DataLabelBase(Serialisable):
    numFmt: Any
    spPr: Any
    graphicalProperties: Any
    txPr: Any
    textProperties: Any
    dLblPos: Any
    position: Any
    showLegendKey: Any
    showVal: Any
    showCatName: Any
    showSerName: Any
    showPercent: Any
    showBubbleSize: Any
    showLeaderLines: Any
    separator: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        numFmt: Any | None = ...,
        spPr: Any | None = ...,
        txPr: Any | None = ...,
        dLblPos: Any | None = ...,
        showLegendKey: Any | None = ...,
        showVal: Any | None = ...,
        showCatName: Any | None = ...,
        showSerName: Any | None = ...,
        showPercent: Any | None = ...,
        showBubbleSize: Any | None = ...,
        showLeaderLines: Any | None = ...,
        separator: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
    @property
    @abstractmethod
    def tagname(self) -> str: ...

class DataLabel(_DataLabelBase):
    tagname: str
    idx: Any
    numFmt: Any
    spPr: Any
    txPr: Any
    dLblPos: Any
    showLegendKey: Any
    showVal: Any
    showCatName: Any
    showSerName: Any
    showPercent: Any
    showBubbleSize: Any
    showLeaderLines: Any
    separator: Any
    extLst: Any
    __elements__: Any
    def __init__(self, idx: int = ..., **kw) -> None: ...

class DataLabelList(_DataLabelBase):
    tagname: str
    dLbl: Any
    delete: Any
    numFmt: Any
    spPr: Any
    txPr: Any
    dLblPos: Any
    showLegendKey: Any
    showVal: Any
    showCatName: Any
    showSerName: Any
    showPercent: Any
    showBubbleSize: Any
    showLeaderLines: Any
    separator: Any
    extLst: Any
    __elements__: Any
    def __init__(self, dLbl=..., delete: Any | None = ..., **kw) -> None: ...
