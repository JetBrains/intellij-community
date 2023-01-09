from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Break(Serialisable):
    tagname: str
    id: Any
    min: Any
    max: Any
    man: Any
    pt: Any
    def __init__(self, id: int = ..., min: int = ..., max: int = ..., man: bool = ..., pt: Any | None = ...) -> None: ...

class RowBreak(Serialisable):
    tagname: str
    # Overwritten by properties below
    # count: Integer
    # manualBreakCount: Integer
    brk: Any
    __elements__: Any
    __attrs__: Any
    def __init__(self, count: Any | None = ..., manualBreakCount: Any | None = ..., brk=...) -> None: ...
    def __bool__(self): ...
    def __len__(self): ...
    @property
    def count(self): ...
    @property
    def manualBreakCount(self): ...
    def append(self, brk: Any | None = ...) -> None: ...

PageBreak = RowBreak

class ColBreak(RowBreak):
    tagname: str
    @property
    def count(self): ...
    @property
    def manualBreakCount(self): ...
    brk: Any
    __attrs__: Any
