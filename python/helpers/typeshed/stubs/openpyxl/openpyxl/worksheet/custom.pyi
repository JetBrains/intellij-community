from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class CustomProperty(Serialisable):
    tagname: str
    name: Any
    def __init__(self, name: Any | None = ...) -> None: ...

class CustomProperties(Serialisable):
    tagname: str
    customPr: Any
    __elements__: Any
    def __init__(self, customPr=...) -> None: ...
