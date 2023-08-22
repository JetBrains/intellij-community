from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class FunctionGroup(Serialisable):
    tagname: str
    name: Any
    def __init__(self, name: Any | None = ...) -> None: ...

class FunctionGroupList(Serialisable):
    tagname: str
    builtInGroupCount: Any
    functionGroup: Any
    __elements__: Any
    def __init__(self, builtInGroupCount: int = ..., functionGroup=...) -> None: ...
