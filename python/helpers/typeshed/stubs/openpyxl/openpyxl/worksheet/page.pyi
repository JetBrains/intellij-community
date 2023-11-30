from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal, Self, TypeAlias

from openpyxl.descriptors.base import Bool, Float, Integer, NoneSet, _ConvertibleToBool, _ConvertibleToFloat, _ConvertibleToInt
from openpyxl.descriptors.serialisable import Serialisable, _ChildSerialisableTreeElement
from openpyxl.worksheet.properties import PageSetupProperties

_PrintPageSetupOrientation: TypeAlias = Literal["default", "portrait", "landscape"]
_PrintPageSetupPageOrder: TypeAlias = Literal["downThenOver", "overThenDown"]
_PrintPageSetupCellComments: TypeAlias = Literal["asDisplayed", "atEnd"]
_PrintPageSetupErrors: TypeAlias = Literal["displayed", "blank", "dash", "NA"]

class PrintPageSetup(Serialisable):
    tagname: ClassVar[str]
    orientation: NoneSet[_PrintPageSetupOrientation]
    paperSize: Integer[Literal[True]]
    scale: Integer[Literal[True]]
    fitToHeight: Integer[Literal[True]]
    fitToWidth: Integer[Literal[True]]
    firstPageNumber: Integer[Literal[True]]
    useFirstPageNumber: Bool[Literal[True]]
    paperHeight: Incomplete
    paperWidth: Incomplete
    pageOrder: NoneSet[_PrintPageSetupPageOrder]
    usePrinterDefaults: Bool[Literal[True]]
    blackAndWhite: Bool[Literal[True]]
    draft: Bool[Literal[True]]
    cellComments: NoneSet[_PrintPageSetupCellComments]
    errors: NoneSet[_PrintPageSetupErrors]
    horizontalDpi: Integer[Literal[True]]
    verticalDpi: Integer[Literal[True]]
    copies: Integer[Literal[True]]
    id: Incomplete
    def __init__(
        self,
        worksheet: Incomplete | None = None,
        orientation: _PrintPageSetupOrientation | Literal["none"] | None = None,
        paperSize: _ConvertibleToInt | None = None,
        scale: _ConvertibleToInt | None = None,
        fitToHeight: _ConvertibleToInt | None = None,
        fitToWidth: _ConvertibleToInt | None = None,
        firstPageNumber: _ConvertibleToInt | None = None,
        useFirstPageNumber: _ConvertibleToBool | None = None,
        paperHeight: Incomplete | None = None,
        paperWidth: Incomplete | None = None,
        pageOrder: _PrintPageSetupPageOrder | Literal["none"] | None = None,
        usePrinterDefaults: _ConvertibleToBool | None = None,
        blackAndWhite: _ConvertibleToBool | None = None,
        draft: _ConvertibleToBool | None = None,
        cellComments: _PrintPageSetupCellComments | Literal["none"] | None = None,
        errors: _PrintPageSetupErrors | Literal["none"] | None = None,
        horizontalDpi: _ConvertibleToInt | None = None,
        verticalDpi: _ConvertibleToInt | None = None,
        copies: _ConvertibleToInt | None = None,
        id: Incomplete | None = None,
    ) -> None: ...
    def __bool__(self) -> bool: ...
    @property
    def sheet_properties(self) -> PageSetupProperties | None: ...
    @property
    def fitToPage(self) -> bool | None: ...
    @fitToPage.setter
    def fitToPage(self, value: _ConvertibleToBool | None) -> None: ...
    @property
    def autoPageBreaks(self) -> bool | None: ...
    @autoPageBreaks.setter
    def autoPageBreaks(self, value: _ConvertibleToBool | None) -> None: ...
    @classmethod
    def from_tree(cls, node: _ChildSerialisableTreeElement) -> Self: ...

class PrintOptions(Serialisable):
    tagname: ClassVar[str]
    horizontalCentered: Bool[Literal[True]]
    verticalCentered: Bool[Literal[True]]
    headings: Bool[Literal[True]]
    gridLines: Bool[Literal[True]]
    gridLinesSet: Bool[Literal[True]]
    def __init__(
        self,
        horizontalCentered: _ConvertibleToBool | None = None,
        verticalCentered: _ConvertibleToBool | None = None,
        headings: _ConvertibleToBool | None = None,
        gridLines: _ConvertibleToBool | None = None,
        gridLinesSet: _ConvertibleToBool | None = None,
    ) -> None: ...
    def __bool__(self) -> bool: ...

class PageMargins(Serialisable):
    tagname: ClassVar[str]
    left: Float[Literal[False]]
    right: Float[Literal[False]]
    top: Float[Literal[False]]
    bottom: Float[Literal[False]]
    header: Float[Literal[False]]
    footer: Float[Literal[False]]
    def __init__(
        self,
        left: _ConvertibleToFloat = 0.75,
        right: _ConvertibleToFloat = 0.75,
        top: _ConvertibleToFloat = 1,
        bottom: _ConvertibleToFloat = 1,
        header: _ConvertibleToFloat = 0.5,
        footer: _ConvertibleToFloat = 0.5,
    ) -> None: ...
