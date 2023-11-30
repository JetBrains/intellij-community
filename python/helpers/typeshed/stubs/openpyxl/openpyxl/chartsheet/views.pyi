from _typeshed import Incomplete, Unused
from typing import ClassVar
from typing_extensions import Literal

from openpyxl.descriptors.base import Bool, Integer, Typed, _ConvertibleToBool, _ConvertibleToInt
from openpyxl.descriptors.excel import ExtensionList
from openpyxl.descriptors.serialisable import Serialisable

class ChartsheetView(Serialisable):
    tagname: ClassVar[str]
    tabSelected: Bool[Literal[True]]
    zoomScale: Integer[Literal[True]]
    workbookViewId: Integer[Literal[False]]
    zoomToFit: Bool[Literal[True]]
    extLst: Typed[ExtensionList, Literal[True]]
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(
        self,
        tabSelected: _ConvertibleToBool | None = None,
        zoomScale: _ConvertibleToInt | None = None,
        workbookViewId: _ConvertibleToInt = 0,
        zoomToFit: _ConvertibleToBool | None = True,
        extLst: Unused = None,
    ) -> None: ...

class ChartsheetViewList(Serialisable):
    tagname: ClassVar[str]
    sheetView: Incomplete
    extLst: Typed[ExtensionList, Literal[True]]
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(self, sheetView: Incomplete | None = None, extLst: Unused = None) -> None: ...
