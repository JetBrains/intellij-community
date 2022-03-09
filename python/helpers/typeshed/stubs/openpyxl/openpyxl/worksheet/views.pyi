from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Pane(Serialisable):  # type: ignore[misc]
    xSplit: Any
    ySplit: Any
    topLeftCell: Any
    activePane: Any
    state: Any
    def __init__(
        self,
        xSplit: Any | None = ...,
        ySplit: Any | None = ...,
        topLeftCell: Any | None = ...,
        activePane: str = ...,
        state: str = ...,
    ) -> None: ...

class Selection(Serialisable):  # type: ignore[misc]
    pane: Any
    activeCell: Any
    activeCellId: Any
    sqref: Any
    def __init__(
        self, pane: Any | None = ..., activeCell: str = ..., activeCellId: Any | None = ..., sqref: str = ...
    ) -> None: ...

class SheetView(Serialisable):
    tagname: str
    windowProtection: Any
    showFormulas: Any
    showGridLines: Any
    showRowColHeaders: Any
    showZeros: Any
    rightToLeft: Any
    tabSelected: Any
    showRuler: Any
    showOutlineSymbols: Any
    defaultGridColor: Any
    showWhiteSpace: Any
    view: Any
    topLeftCell: Any
    colorId: Any
    zoomScale: Any
    zoomScaleNormal: Any
    zoomScaleSheetLayoutView: Any
    zoomScalePageLayoutView: Any
    zoomToFit: Any
    workbookViewId: Any
    selection: Any
    pane: Any
    def __init__(
        self,
        windowProtection: Any | None = ...,
        showFormulas: Any | None = ...,
        showGridLines: Any | None = ...,
        showRowColHeaders: Any | None = ...,
        showZeros: Any | None = ...,
        rightToLeft: Any | None = ...,
        tabSelected: Any | None = ...,
        showRuler: Any | None = ...,
        showOutlineSymbols: Any | None = ...,
        defaultGridColor: Any | None = ...,
        showWhiteSpace: Any | None = ...,
        view: Any | None = ...,
        topLeftCell: Any | None = ...,
        colorId: Any | None = ...,
        zoomScale: Any | None = ...,
        zoomScaleNormal: Any | None = ...,
        zoomScaleSheetLayoutView: Any | None = ...,
        zoomScalePageLayoutView: Any | None = ...,
        zoomToFit: Any | None = ...,
        workbookViewId: int = ...,
        selection: Any | None = ...,
        pane: Any | None = ...,
    ) -> None: ...

class SheetViewList(Serialisable):
    tagname: str
    sheetView: Any
    extLst: Any
    __elements__: Any
    def __init__(self, sheetView: Any | None = ..., extLst: Any | None = ...) -> None: ...
