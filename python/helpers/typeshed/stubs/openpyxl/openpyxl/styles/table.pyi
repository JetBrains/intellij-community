from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class TableStyleElement(Serialisable):
    tagname: str
    type: Any
    size: Any
    dxfId: Any
    def __init__(self, type: Any | None = ..., size: Any | None = ..., dxfId: Any | None = ...) -> None: ...

class TableStyle(Serialisable):
    tagname: str
    name: Any
    pivot: Any
    table: Any
    count: Any
    tableStyleElement: Any
    __elements__: Any
    def __init__(
        self,
        name: Any | None = ...,
        pivot: Any | None = ...,
        table: Any | None = ...,
        count: Any | None = ...,
        tableStyleElement=...,
    ) -> None: ...

class TableStyleList(Serialisable):
    tagname: str
    defaultTableStyle: Any
    defaultPivotStyle: Any
    tableStyle: Any
    __elements__: Any
    __attrs__: Any
    def __init__(
        self, count: Any | None = ..., defaultTableStyle: str = ..., defaultPivotStyle: str = ..., tableStyle=...
    ) -> None: ...
    @property
    def count(self): ...
