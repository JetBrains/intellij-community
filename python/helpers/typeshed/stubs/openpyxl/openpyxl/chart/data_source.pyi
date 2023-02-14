from typing import Any

from openpyxl.descriptors.nested import NestedText
from openpyxl.descriptors.serialisable import Serialisable

class NumFmt(Serialisable):  # type: ignore[misc]
    formatCode: Any
    sourceLinked: Any
    def __init__(self, formatCode: Any | None = ..., sourceLinked: bool = ...) -> None: ...

class NumberValueDescriptor(NestedText):
    allow_none: bool
    expected_type: Any
    def __set__(self, instance, value) -> None: ...

class NumVal(Serialisable):  # type: ignore[misc]
    idx: Any
    formatCode: Any
    v: Any
    def __init__(self, idx: Any | None = ..., formatCode: Any | None = ..., v: Any | None = ...) -> None: ...

class NumData(Serialisable):  # type: ignore[misc]
    formatCode: Any
    ptCount: Any
    pt: Any
    extLst: Any
    __elements__: Any
    def __init__(self, formatCode: Any | None = ..., ptCount: Any | None = ..., pt=..., extLst: Any | None = ...) -> None: ...

class NumRef(Serialisable):  # type: ignore[misc]
    f: Any
    ref: Any
    numCache: Any
    extLst: Any
    __elements__: Any
    def __init__(self, f: Any | None = ..., numCache: Any | None = ..., extLst: Any | None = ...) -> None: ...

class StrVal(Serialisable):
    tagname: str
    idx: Any
    v: Any
    def __init__(self, idx: int = ..., v: Any | None = ...) -> None: ...

class StrData(Serialisable):
    tagname: str
    ptCount: Any
    pt: Any
    extLst: Any
    __elements__: Any
    def __init__(self, ptCount: Any | None = ..., pt=..., extLst: Any | None = ...) -> None: ...

class StrRef(Serialisable):
    tagname: str
    f: Any
    strCache: Any
    extLst: Any
    __elements__: Any
    def __init__(self, f: Any | None = ..., strCache: Any | None = ..., extLst: Any | None = ...) -> None: ...

class NumDataSource(Serialisable):  # type: ignore[misc]
    numRef: Any
    numLit: Any
    def __init__(self, numRef: Any | None = ..., numLit: Any | None = ...) -> None: ...

class Level(Serialisable):
    tagname: str
    pt: Any
    __elements__: Any
    def __init__(self, pt=...) -> None: ...

class MultiLevelStrData(Serialisable):
    tagname: str
    ptCount: Any
    lvl: Any
    extLst: Any
    __elements__: Any
    def __init__(self, ptCount: Any | None = ..., lvl=..., extLst: Any | None = ...) -> None: ...

class MultiLevelStrRef(Serialisable):
    tagname: str
    f: Any
    multiLvlStrCache: Any
    extLst: Any
    __elements__: Any
    def __init__(self, f: Any | None = ..., multiLvlStrCache: Any | None = ..., extLst: Any | None = ...) -> None: ...

class AxDataSource(Serialisable):
    tagname: str
    numRef: Any
    numLit: Any
    strRef: Any
    strLit: Any
    multiLvlStrRef: Any
    def __init__(
        self,
        numRef: Any | None = ...,
        numLit: Any | None = ...,
        strRef: Any | None = ...,
        strLit: Any | None = ...,
        multiLvlStrRef: Any | None = ...,
    ) -> None: ...
