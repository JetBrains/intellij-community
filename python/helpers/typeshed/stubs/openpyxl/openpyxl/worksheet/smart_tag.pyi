from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class CellSmartTagPr(Serialisable):
    tagname: str
    key: Any
    val: Any
    def __init__(self, key: Any | None = ..., val: Any | None = ...) -> None: ...

class CellSmartTag(Serialisable):
    tagname: str
    cellSmartTagPr: Any
    type: Any
    deleted: Any
    xmlBased: Any
    __elements__: Any
    def __init__(self, cellSmartTagPr=..., type: Any | None = ..., deleted: bool = ..., xmlBased: bool = ...) -> None: ...

class CellSmartTags(Serialisable):
    tagname: str
    cellSmartTag: Any
    r: Any
    __elements__: Any
    def __init__(self, cellSmartTag=..., r: Any | None = ...) -> None: ...

class SmartTags(Serialisable):
    tagname: str
    cellSmartTags: Any
    __elements__: Any
    def __init__(self, cellSmartTags=...) -> None: ...
