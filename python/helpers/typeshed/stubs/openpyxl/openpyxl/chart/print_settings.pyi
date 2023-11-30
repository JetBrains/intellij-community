from typing import ClassVar
from typing_extensions import Literal

from openpyxl.descriptors.base import Alias, Float, Typed, _ConvertibleToFloat
from openpyxl.descriptors.serialisable import Serialisable
from openpyxl.worksheet.header_footer import HeaderFooter
from openpyxl.worksheet.page import PrintPageSetup

class PageMargins(Serialisable):
    tagname: ClassVar[str]
    l: Float[Literal[False]]
    left: Alias
    r: Float[Literal[False]]
    right: Alias
    t: Float[Literal[False]]
    top: Alias
    b: Float[Literal[False]]
    bottom: Alias
    header: Float[Literal[False]]
    footer: Float[Literal[False]]
    def __init__(
        self,
        l: _ConvertibleToFloat = 0.75,
        r: _ConvertibleToFloat = 0.75,
        t: _ConvertibleToFloat = 1,
        b: _ConvertibleToFloat = 1,
        header: _ConvertibleToFloat = 0.5,
        footer: _ConvertibleToFloat = 0.5,
    ) -> None: ...

class PrintSettings(Serialisable):
    tagname: ClassVar[str]
    headerFooter: Typed[HeaderFooter, Literal[True]]
    pageMargins: Typed[PageMargins, Literal[True]]
    pageSetup: Typed[PrintPageSetup, Literal[True]]
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(
        self,
        headerFooter: HeaderFooter | None = None,
        pageMargins: PageMargins | None = None,
        pageSetup: PrintPageSetup | None = None,
    ) -> None: ...
