from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class ManualLayout(Serialisable):
    tagname: str
    layoutTarget: Any
    xMode: Any
    yMode: Any
    wMode: Any
    hMode: Any
    x: Any
    y: Any
    w: Any
    width: Any
    h: Any
    height: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        layoutTarget: Any | None = ...,
        xMode: Any | None = ...,
        yMode: Any | None = ...,
        wMode: str = ...,
        hMode: str = ...,
        x: Any | None = ...,
        y: Any | None = ...,
        w: Any | None = ...,
        h: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class Layout(Serialisable):
    tagname: str
    manualLayout: Any
    extLst: Any
    __elements__: Any
    def __init__(self, manualLayout: Any | None = ..., extLst: Any | None = ...) -> None: ...
