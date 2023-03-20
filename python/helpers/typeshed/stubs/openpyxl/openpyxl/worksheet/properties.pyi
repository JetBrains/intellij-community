from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Outline(Serialisable):
    tagname: str
    applyStyles: Any
    summaryBelow: Any
    summaryRight: Any
    showOutlineSymbols: Any
    def __init__(
        self,
        applyStyles: Any | None = ...,
        summaryBelow: Any | None = ...,
        summaryRight: Any | None = ...,
        showOutlineSymbols: Any | None = ...,
    ) -> None: ...

class PageSetupProperties(Serialisable):
    tagname: str
    autoPageBreaks: Any
    fitToPage: Any
    def __init__(self, autoPageBreaks: Any | None = ..., fitToPage: Any | None = ...) -> None: ...

class WorksheetProperties(Serialisable):
    tagname: str
    codeName: Any
    enableFormatConditionsCalculation: Any
    filterMode: Any
    published: Any
    syncHorizontal: Any
    syncRef: Any
    syncVertical: Any
    transitionEvaluation: Any
    transitionEntry: Any
    tabColor: Any
    outlinePr: Any
    pageSetUpPr: Any
    __elements__: Any
    def __init__(
        self,
        codeName: Any | None = ...,
        enableFormatConditionsCalculation: Any | None = ...,
        filterMode: Any | None = ...,
        published: Any | None = ...,
        syncHorizontal: Any | None = ...,
        syncRef: Any | None = ...,
        syncVertical: Any | None = ...,
        transitionEvaluation: Any | None = ...,
        transitionEntry: Any | None = ...,
        tabColor: Any | None = ...,
        outlinePr: Any | None = ...,
        pageSetUpPr: Any | None = ...,
    ) -> None: ...
