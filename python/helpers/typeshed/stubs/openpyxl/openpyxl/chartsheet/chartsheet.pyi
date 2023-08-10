from typing import Any

from openpyxl.descriptors.serialisable import Serialisable
from openpyxl.workbook.child import _WorkbookChild

class Chartsheet(_WorkbookChild, Serialisable):
    tagname: str
    mime_type: str
    sheetPr: Any
    sheetViews: Any
    sheetProtection: Any
    customSheetViews: Any
    pageMargins: Any
    pageSetup: Any
    drawing: Any
    drawingHF: Any
    picture: Any
    webPublishItems: Any
    extLst: Any
    sheet_state: Any
    headerFooter: Any
    HeaderFooter: Any
    __elements__: Any
    __attrs__: Any
    def __init__(
        self,
        sheetPr: Any | None = ...,
        sheetViews: Any | None = ...,
        sheetProtection: Any | None = ...,
        customSheetViews: Any | None = ...,
        pageMargins: Any | None = ...,
        pageSetup: Any | None = ...,
        headerFooter: Any | None = ...,
        drawing: Any | None = ...,
        drawingHF: Any | None = ...,
        picture: Any | None = ...,
        webPublishItems: Any | None = ...,
        extLst: Any | None = ...,
        parent: Any | None = ...,
        title: str = ...,
        sheet_state: str = ...,
    ) -> None: ...
    def add_chart(self, chart) -> None: ...
    def to_tree(self): ...
