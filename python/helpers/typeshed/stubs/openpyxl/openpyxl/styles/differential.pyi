from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class DifferentialStyle(Serialisable):
    tagname: str
    __elements__: Any
    font: Any
    numFmt: Any
    fill: Any
    alignment: Any
    border: Any
    protection: Any
    extLst: Any
    def __init__(
        self,
        font: Any | None = ...,
        numFmt: Any | None = ...,
        fill: Any | None = ...,
        alignment: Any | None = ...,
        border: Any | None = ...,
        protection: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class DifferentialStyleList(Serialisable):
    tagname: str
    dxf: Any
    styles: Any
    __attrs__: Any
    def __init__(self, dxf=..., count: Any | None = ...) -> None: ...
    def append(self, dxf) -> None: ...
    def add(self, dxf): ...
    def __bool__(self): ...
    def __getitem__(self, idx): ...
    @property
    def count(self): ...
