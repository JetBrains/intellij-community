from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class BookView(Serialisable):
    tagname: str
    visibility: Any
    minimized: Any
    showHorizontalScroll: Any
    showVerticalScroll: Any
    showSheetTabs: Any
    xWindow: Any
    yWindow: Any
    windowWidth: Any
    windowHeight: Any
    tabRatio: Any
    firstSheet: Any
    activeTab: Any
    autoFilterDateGrouping: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        visibility: str = ...,
        minimized: bool = ...,
        showHorizontalScroll: bool = ...,
        showVerticalScroll: bool = ...,
        showSheetTabs: bool = ...,
        xWindow: Any | None = ...,
        yWindow: Any | None = ...,
        windowWidth: Any | None = ...,
        windowHeight: Any | None = ...,
        tabRatio: int = ...,
        firstSheet: int = ...,
        activeTab: int = ...,
        autoFilterDateGrouping: bool = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class CustomWorkbookView(Serialisable):
    tagname: str
    name: Any
    guid: Any
    autoUpdate: Any
    mergeInterval: Any
    changesSavedWin: Any
    onlySync: Any
    personalView: Any
    includePrintSettings: Any
    includeHiddenRowCol: Any
    maximized: Any
    minimized: Any
    showHorizontalScroll: Any
    showVerticalScroll: Any
    showSheetTabs: Any
    xWindow: Any
    yWindow: Any
    windowWidth: Any
    windowHeight: Any
    tabRatio: Any
    activeSheetId: Any
    showFormulaBar: Any
    showStatusbar: Any
    showComments: Any
    showObjects: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        name: Any | None = ...,
        guid: Any | None = ...,
        autoUpdate: Any | None = ...,
        mergeInterval: Any | None = ...,
        changesSavedWin: Any | None = ...,
        onlySync: Any | None = ...,
        personalView: Any | None = ...,
        includePrintSettings: Any | None = ...,
        includeHiddenRowCol: Any | None = ...,
        maximized: Any | None = ...,
        minimized: Any | None = ...,
        showHorizontalScroll: Any | None = ...,
        showVerticalScroll: Any | None = ...,
        showSheetTabs: Any | None = ...,
        xWindow: Any | None = ...,
        yWindow: Any | None = ...,
        windowWidth: Any | None = ...,
        windowHeight: Any | None = ...,
        tabRatio: Any | None = ...,
        activeSheetId: Any | None = ...,
        showFormulaBar: Any | None = ...,
        showStatusbar: Any | None = ...,
        showComments: str = ...,
        showObjects: str = ...,
        extLst: Any | None = ...,
    ) -> None: ...
