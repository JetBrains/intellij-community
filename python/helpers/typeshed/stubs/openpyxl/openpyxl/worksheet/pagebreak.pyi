from _typeshed import Incomplete, Unused
from typing import ClassVar
from typing_extensions import Literal

from openpyxl.descriptors.base import Bool, Integer, _ConvertibleToBool, _ConvertibleToInt
from openpyxl.descriptors.serialisable import Serialisable

class Break(Serialisable):
    tagname: ClassVar[str]
    id: Integer[Literal[True]]
    min: Integer[Literal[True]]
    max: Integer[Literal[True]]
    man: Bool[Literal[True]]
    pt: Bool[Literal[True]]
    def __init__(
        self,
        id: _ConvertibleToInt | None = 0,
        min: _ConvertibleToInt | None = 0,
        max: _ConvertibleToInt | None = 16383,
        man: _ConvertibleToBool | None = True,
        pt: _ConvertibleToBool | None = None,
    ) -> None: ...

class RowBreak(Serialisable):
    tagname: ClassVar[str]
    # Overwritten by properties below
    # count: Integer
    # manualBreakCount: Integer
    brk: Incomplete
    __elements__: ClassVar[tuple[str, ...]]
    __attrs__: ClassVar[tuple[str, ...]]
    def __init__(self, count: Unused = None, manualBreakCount: Unused = None, brk=()) -> None: ...
    def __bool__(self) -> bool: ...
    def __len__(self) -> int: ...
    @property
    def count(self) -> int: ...
    @property
    def manualBreakCount(self) -> int: ...
    def append(self, brk: Incomplete | None = None) -> None: ...

PageBreak = RowBreak

class ColBreak(RowBreak):
    tagname: ClassVar[str]
    # Same as parent
    # count = RowBreak.count
    # manualBreakCount = RowBreak.manualBreakCount
    # brk = RowBreak.brk
    __attrs__: ClassVar[tuple[str, ...]]
