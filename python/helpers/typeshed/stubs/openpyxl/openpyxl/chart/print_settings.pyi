from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class PageMargins(Serialisable):
    tagname: str
    l: Any
    left: Any
    r: Any
    right: Any
    t: Any
    top: Any
    b: Any
    bottom: Any
    header: Any
    footer: Any
    def __init__(
        self, l: float = ..., r: float = ..., t: int = ..., b: int = ..., header: float = ..., footer: float = ...
    ) -> None: ...

class PrintSettings(Serialisable):
    tagname: str
    headerFooter: Any
    pageMargins: Any
    pageSetup: Any
    __elements__: Any
    def __init__(self, headerFooter: Any | None = ..., pageMargins: Any | None = ..., pageSetup: Any | None = ...) -> None: ...
