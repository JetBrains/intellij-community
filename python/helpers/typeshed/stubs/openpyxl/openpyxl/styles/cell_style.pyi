from array import array
from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class ArrayDescriptor:
    key: Any
    def __init__(self, key) -> None: ...
    def __get__(self, instance, cls): ...
    def __set__(self, instance, value) -> None: ...

class StyleArray(array[Any]):
    tagname: str
    fontId: Any
    fillId: Any
    borderId: Any
    numFmtId: Any
    protectionId: Any
    alignmentId: Any
    pivotButton: Any
    quotePrefix: Any
    xfId: Any
    def __new__(cls, args=...): ...
    def __hash__(self): ...
    def __copy__(self): ...
    def __deepcopy__(self, memo): ...

class CellStyle(Serialisable):
    tagname: str
    numFmtId: Any
    fontId: Any
    fillId: Any
    borderId: Any
    xfId: Any
    quotePrefix: Any
    pivotButton: Any
    applyNumberFormat: Any
    applyFont: Any
    applyFill: Any
    applyBorder: Any
    # Overwritten by properties below
    # applyAlignment: Bool
    # applyProtection: Bool
    alignment: Any
    protection: Any
    extLst: Any
    __elements__: Any
    __attrs__: Any
    def __init__(
        self,
        numFmtId: int = ...,
        fontId: int = ...,
        fillId: int = ...,
        borderId: int = ...,
        xfId: Any | None = ...,
        quotePrefix: Any | None = ...,
        pivotButton: Any | None = ...,
        applyNumberFormat: Any | None = ...,
        applyFont: Any | None = ...,
        applyFill: Any | None = ...,
        applyBorder: Any | None = ...,
        applyAlignment: Any | None = ...,
        applyProtection: Any | None = ...,
        alignment: Any | None = ...,
        protection: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
    def to_array(self): ...
    @classmethod
    def from_array(cls, style): ...
    @property
    def applyProtection(self): ...
    @property
    def applyAlignment(self): ...

class CellStyleList(Serialisable):
    tagname: str
    __attrs__: Any
    # Overwritten by property below
    # count: Integer
    xf: Any
    alignment: Any
    protection: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., xf=...) -> None: ...
    @property
    def count(self): ...
    def __getitem__(self, idx): ...
