from _typeshed import Incomplete, Unused
from collections.abc import Iterator
from typing import ClassVar
from typing_extensions import Literal

from openpyxl.descriptors.base import Bool, Integer, String, Typed, _ConvertibleToBool, _ConvertibleToInt
from openpyxl.descriptors.excel import ExtensionList
from openpyxl.descriptors.serialisable import Serialisable
from openpyxl.styles.alignment import Alignment
from openpyxl.styles.borders import Border
from openpyxl.styles.fills import Fill
from openpyxl.styles.fonts import Font
from openpyxl.styles.protection import Protection

class NamedStyle(Serialisable):
    font: Typed[Font, Literal[False]]
    fill: Typed[Fill, Literal[False]]
    border: Typed[Border, Literal[False]]
    alignment: Typed[Alignment, Literal[False]]
    number_format: Incomplete
    protection: Typed[Protection, Literal[False]]
    builtinId: Integer[Literal[True]]
    hidden: Bool[Literal[True]]
    # Overwritten by property below
    # xfId: Integer
    name: String[Literal[False]]
    def __init__(
        self,
        name: str = "Normal",
        font: Font | None = None,
        fill: Fill | None = None,
        border: Border | None = None,
        alignment: Alignment | None = None,
        number_format: Incomplete | None = None,
        protection: Protection | None = None,
        builtinId: _ConvertibleToInt | None = None,
        hidden: _ConvertibleToBool | None = False,
        xfId: Unused = None,
    ) -> None: ...
    def __setattr__(self, attr: str, value) -> None: ...
    def __iter__(self) -> Iterator[tuple[str, str]]: ...
    @property
    def xfId(self) -> int | None: ...
    def bind(self, wb) -> None: ...
    def as_tuple(self): ...
    def as_xf(self): ...
    def as_name(self): ...

class NamedStyleList(list[Incomplete]):
    @property
    def names(self) -> list[str]: ...
    def __getitem__(self, key): ...
    def append(self, style) -> None: ...

class _NamedCellStyle(Serialisable):
    tagname: ClassVar[str]
    name: String[Literal[False]]
    xfId: Integer[Literal[False]]
    builtinId: Integer[Literal[True]]
    iLevel: Integer[Literal[True]]
    hidden: Bool[Literal[True]]
    customBuiltin: Bool[Literal[True]]
    extLst: Typed[ExtensionList, Literal[True]]
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(
        self,
        name: str,
        xfId: _ConvertibleToInt,
        builtinId: _ConvertibleToInt | None = None,
        iLevel: _ConvertibleToInt | None = None,
        hidden: _ConvertibleToBool | None = None,
        customBuiltin: _ConvertibleToBool | None = None,
        extLst: Unused = None,
    ) -> None: ...

class _NamedCellStyleList(Serialisable):
    tagname: ClassVar[str]
    # Overwritten by property below
    # count: Integer
    cellStyle: Incomplete
    __attrs__: ClassVar[tuple[str, ...]]
    def __init__(self, count: Unused = None, cellStyle=()) -> None: ...
    @property
    def count(self) -> int: ...
    @property
    def names(self) -> NamedStyleList: ...
