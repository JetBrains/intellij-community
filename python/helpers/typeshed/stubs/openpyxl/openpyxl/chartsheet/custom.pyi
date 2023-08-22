from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class CustomChartsheetView(Serialisable):
    tagname: str
    guid: Any
    scale: Any
    state: Any
    zoomToFit: Any
    pageMargins: Any
    pageSetup: Any
    headerFooter: Any
    __elements__: Any
    def __init__(
        self,
        guid: Any | None = ...,
        scale: Any | None = ...,
        state: str = ...,
        zoomToFit: Any | None = ...,
        pageMargins: Any | None = ...,
        pageSetup: Any | None = ...,
        headerFooter: Any | None = ...,
    ) -> None: ...

class CustomChartsheetViews(Serialisable):
    tagname: str
    customSheetView: Any
    __elements__: Any
    def __init__(self, customSheetView: Any | None = ...) -> None: ...
