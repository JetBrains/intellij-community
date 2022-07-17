from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class AnchorClientData(Serialisable):
    fLocksWithSheet: Any
    fPrintsWithSheet: Any
    def __init__(self, fLocksWithSheet: Any | None = ..., fPrintsWithSheet: Any | None = ...) -> None: ...

class AnchorMarker(Serialisable):
    tagname: str
    col: Any
    colOff: Any
    row: Any
    rowOff: Any
    def __init__(self, col: int = ..., colOff: int = ..., row: int = ..., rowOff: int = ...) -> None: ...

class _AnchorBase(Serialisable):
    sp: Any
    shape: Any
    grpSp: Any
    groupShape: Any
    graphicFrame: Any
    cxnSp: Any
    connectionShape: Any
    pic: Any
    contentPart: Any
    clientData: Any
    __elements__: Any
    def __init__(
        self,
        clientData: Any | None = ...,
        sp: Any | None = ...,
        grpSp: Any | None = ...,
        graphicFrame: Any | None = ...,
        cxnSp: Any | None = ...,
        pic: Any | None = ...,
        contentPart: Any | None = ...,
    ) -> None: ...

class AbsoluteAnchor(_AnchorBase):
    tagname: str
    pos: Any
    ext: Any
    sp: Any
    grpSp: Any
    graphicFrame: Any
    cxnSp: Any
    pic: Any
    contentPart: Any
    clientData: Any
    __elements__: Any
    def __init__(self, pos: Any | None = ..., ext: Any | None = ..., **kw) -> None: ...

class OneCellAnchor(_AnchorBase):
    tagname: str
    ext: Any
    sp: Any
    grpSp: Any
    graphicFrame: Any
    cxnSp: Any
    pic: Any
    contentPart: Any
    clientData: Any
    __elements__: Any
    def __init__(self, _from: Any | None = ..., ext: Any | None = ..., **kw) -> None: ...

class TwoCellAnchor(_AnchorBase):
    tagname: str
    editAs: Any
    to: Any
    sp: Any
    grpSp: Any
    graphicFrame: Any
    cxnSp: Any
    pic: Any
    contentPart: Any
    clientData: Any
    __elements__: Any
    def __init__(self, editAs: Any | None = ..., _from: Any | None = ..., to: Any | None = ..., **kw) -> None: ...

class SpreadsheetDrawing(Serialisable):
    tagname: str
    mime_type: str
    PartName: str
    twoCellAnchor: Any
    oneCellAnchor: Any
    absoluteAnchor: Any
    __elements__: Any
    charts: Any
    images: Any
    def __init__(self, twoCellAnchor=..., oneCellAnchor=..., absoluteAnchor=...) -> None: ...
    def __hash__(self): ...
    def __bool__(self): ...
    @property
    def path(self): ...
