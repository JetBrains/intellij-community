from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class SmartTag(Serialisable):
    tagname: str
    namespaceUri: Any
    name: Any
    url: Any
    def __init__(self, namespaceUri: Any | None = ..., name: Any | None = ..., url: Any | None = ...) -> None: ...

class SmartTagList(Serialisable):
    tagname: str
    smartTagType: Any
    __elements__: Any
    def __init__(self, smartTagType=...) -> None: ...

class SmartTagProperties(Serialisable):
    tagname: str
    embed: Any
    show: Any
    def __init__(self, embed: Any | None = ..., show: Any | None = ...) -> None: ...
