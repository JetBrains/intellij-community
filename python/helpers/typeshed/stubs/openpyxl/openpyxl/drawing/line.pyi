from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class LineEndProperties(Serialisable):
    tagname: str
    namespace: Any
    type: Any
    w: Any
    len: Any
    def __init__(self, type: Any | None = ..., w: Any | None = ..., len: Any | None = ...) -> None: ...

class DashStop(Serialisable):
    tagname: str
    namespace: Any
    d: Any
    length: Any
    sp: Any
    space: Any
    def __init__(self, d: int = ..., sp: int = ...) -> None: ...

class DashStopList(Serialisable):
    ds: Any
    def __init__(self, ds: Any | None = ...) -> None: ...

class LineProperties(Serialisable):
    tagname: str
    namespace: Any
    w: Any
    width: Any
    cap: Any
    cmpd: Any
    algn: Any
    noFill: Any
    solidFill: Any
    gradFill: Any
    pattFill: Any
    prstDash: Any
    dashStyle: Any
    custDash: Any
    round: Any
    bevel: Any
    miter: Any
    headEnd: Any
    tailEnd: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        w: Any | None = ...,
        cap: Any | None = ...,
        cmpd: Any | None = ...,
        algn: Any | None = ...,
        noFill: Any | None = ...,
        solidFill: Any | None = ...,
        gradFill: Any | None = ...,
        pattFill: Any | None = ...,
        prstDash: Any | None = ...,
        custDash: Any | None = ...,
        round: Any | None = ...,
        bevel: Any | None = ...,
        miter: Any | None = ...,
        headEnd: Any | None = ...,
        tailEnd: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
