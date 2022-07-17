from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class ChartsheetView(Serialisable):
    tagname: str
    tabSelected: Any
    zoomScale: Any
    workbookViewId: Any
    zoomToFit: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        tabSelected: Any | None = ...,
        zoomScale: Any | None = ...,
        workbookViewId: int = ...,
        zoomToFit: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class ChartsheetViewList(Serialisable):
    tagname: str
    sheetView: Any
    extLst: Any
    __elements__: Any
    def __init__(self, sheetView: Any | None = ..., extLst: Any | None = ...) -> None: ...
