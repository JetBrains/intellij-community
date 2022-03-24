from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class PrintPageSetup(Serialisable):
    tagname: str
    orientation: Any
    paperSize: Any
    scale: Any
    fitToHeight: Any
    fitToWidth: Any
    firstPageNumber: Any
    useFirstPageNumber: Any
    paperHeight: Any
    paperWidth: Any
    pageOrder: Any
    usePrinterDefaults: Any
    blackAndWhite: Any
    draft: Any
    cellComments: Any
    errors: Any
    horizontalDpi: Any
    verticalDpi: Any
    copies: Any
    id: Any
    def __init__(
        self,
        worksheet: Any | None = ...,
        orientation: Any | None = ...,
        paperSize: Any | None = ...,
        scale: Any | None = ...,
        fitToHeight: Any | None = ...,
        fitToWidth: Any | None = ...,
        firstPageNumber: Any | None = ...,
        useFirstPageNumber: Any | None = ...,
        paperHeight: Any | None = ...,
        paperWidth: Any | None = ...,
        pageOrder: Any | None = ...,
        usePrinterDefaults: Any | None = ...,
        blackAndWhite: Any | None = ...,
        draft: Any | None = ...,
        cellComments: Any | None = ...,
        errors: Any | None = ...,
        horizontalDpi: Any | None = ...,
        verticalDpi: Any | None = ...,
        copies: Any | None = ...,
        id: Any | None = ...,
    ) -> None: ...
    def __bool__(self): ...
    @property
    def sheet_properties(self): ...
    @property
    def fitToPage(self): ...
    @fitToPage.setter
    def fitToPage(self, value) -> None: ...
    @property
    def autoPageBreaks(self): ...
    @autoPageBreaks.setter
    def autoPageBreaks(self, value) -> None: ...
    @classmethod
    def from_tree(cls, node): ...

class PrintOptions(Serialisable):
    tagname: str
    horizontalCentered: Any
    verticalCentered: Any
    headings: Any
    gridLines: Any
    gridLinesSet: Any
    def __init__(
        self,
        horizontalCentered: Any | None = ...,
        verticalCentered: Any | None = ...,
        headings: Any | None = ...,
        gridLines: Any | None = ...,
        gridLinesSet: Any | None = ...,
    ) -> None: ...
    def __bool__(self): ...

class PageMargins(Serialisable):
    tagname: str
    left: Any
    right: Any
    top: Any
    bottom: Any
    header: Any
    footer: Any
    def __init__(
        self, left: float = ..., right: float = ..., top: int = ..., bottom: int = ..., header: float = ..., footer: float = ...
    ) -> None: ...
