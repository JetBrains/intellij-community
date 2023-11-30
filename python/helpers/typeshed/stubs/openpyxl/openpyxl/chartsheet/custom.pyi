from _typeshed import Incomplete
from typing import ClassVar, overload
from typing_extensions import Literal

from openpyxl import _VisibilityType
from openpyxl.descriptors.base import Bool, Integer, Set, Typed, _ConvertibleToBool, _ConvertibleToInt
from openpyxl.descriptors.serialisable import Serialisable
from openpyxl.worksheet.header_footer import HeaderFooter
from openpyxl.worksheet.page import PageMargins, PrintPageSetup

class CustomChartsheetView(Serialisable):
    tagname: ClassVar[str]
    guid: Incomplete
    scale: Integer[Literal[False]]
    state: Set[_VisibilityType]
    zoomToFit: Bool[Literal[True]]
    pageMargins: Typed[PageMargins, Literal[True]]
    pageSetup: Typed[PrintPageSetup, Literal[True]]
    headerFooter: Typed[HeaderFooter, Literal[True]]
    __elements__: ClassVar[tuple[str, ...]]
    @overload
    def __init__(
        self,
        guid: Incomplete | None = None,
        *,
        scale: _ConvertibleToInt,
        state: _VisibilityType = "visible",
        zoomToFit: _ConvertibleToBool | None = None,
        pageMargins: PageMargins | None = None,
        pageSetup: PrintPageSetup | None = None,
        headerFooter: HeaderFooter | None = None,
    ) -> None: ...
    @overload
    def __init__(
        self,
        guid: Incomplete | None,
        scale: _ConvertibleToInt,
        state: _VisibilityType = "visible",
        zoomToFit: _ConvertibleToBool | None = None,
        pageMargins: PageMargins | None = None,
        pageSetup: PrintPageSetup | None = None,
        headerFooter: HeaderFooter | None = None,
    ) -> None: ...

class CustomChartsheetViews(Serialisable):
    tagname: ClassVar[str]
    customSheetView: Incomplete
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(self, customSheetView: Incomplete | None = None) -> None: ...
