from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Hyperlink(Serialisable):
    tagname: str
    ref: Any
    location: Any
    tooltip: Any
    display: Any
    id: Any
    target: Any
    __attrs__: Any
    def __init__(
        self,
        ref: Any | None = ...,
        location: Any | None = ...,
        tooltip: Any | None = ...,
        display: Any | None = ...,
        id: Any | None = ...,
        target: Any | None = ...,
    ) -> None: ...

class HyperlinkList(Serialisable):
    tagname: str
    hyperlink: Any
    def __init__(self, hyperlink=...) -> None: ...
    def __bool__(self): ...
    def __len__(self): ...
    def append(self, value) -> None: ...
