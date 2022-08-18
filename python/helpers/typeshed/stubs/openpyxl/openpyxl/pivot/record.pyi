from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Record(Serialisable):
    tagname: str
    m: Any
    n: Any
    b: Any
    e: Any
    s: Any
    d: Any
    x: Any
    def __init__(
        self,
        _fields=...,
        m: Any | None = ...,
        n: Any | None = ...,
        b: Any | None = ...,
        e: Any | None = ...,
        s: Any | None = ...,
        d: Any | None = ...,
        x: Any | None = ...,
    ) -> None: ...

class RecordList(Serialisable):
    mime_type: str
    rel_type: str
    tagname: str
    r: Any
    extLst: Any
    __elements__: Any
    __attrs__: Any
    def __init__(self, count: Any | None = ..., r=..., extLst: Any | None = ...) -> None: ...
    @property
    def count(self): ...
    def to_tree(self): ...
    @property
    def path(self): ...
