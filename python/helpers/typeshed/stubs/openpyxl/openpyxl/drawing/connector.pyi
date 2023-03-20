from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Connection(Serialisable):
    id: Any
    idx: Any
    def __init__(self, id: Any | None = ..., idx: Any | None = ...) -> None: ...

class ConnectorLocking(Serialisable):
    extLst: Any
    def __init__(self, extLst: Any | None = ...) -> None: ...

class NonVisualConnectorProperties(Serialisable):
    cxnSpLocks: Any
    stCxn: Any
    endCxn: Any
    extLst: Any
    def __init__(
        self, cxnSpLocks: Any | None = ..., stCxn: Any | None = ..., endCxn: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...

class ConnectorNonVisual(Serialisable):
    cNvPr: Any
    cNvCxnSpPr: Any
    __elements__: Any
    def __init__(self, cNvPr: Any | None = ..., cNvCxnSpPr: Any | None = ...) -> None: ...

class ConnectorShape(Serialisable):
    tagname: str
    nvCxnSpPr: Any
    spPr: Any
    style: Any
    macro: Any
    fPublished: Any
    def __init__(
        self,
        nvCxnSpPr: Any | None = ...,
        spPr: Any | None = ...,
        style: Any | None = ...,
        macro: Any | None = ...,
        fPublished: Any | None = ...,
    ) -> None: ...

class ShapeMeta(Serialisable):
    tagname: str
    cNvPr: Any
    cNvSpPr: Any
    def __init__(self, cNvPr: Any | None = ..., cNvSpPr: Any | None = ...) -> None: ...

class Shape(Serialisable):
    macro: Any
    textlink: Any
    fPublished: Any
    fLocksText: Any
    nvSpPr: Any
    meta: Any
    spPr: Any
    graphicalProperties: Any
    style: Any
    txBody: Any
    def __init__(
        self,
        macro: Any | None = ...,
        textlink: Any | None = ...,
        fPublished: Any | None = ...,
        fLocksText: Any | None = ...,
        nvSpPr: Any | None = ...,
        spPr: Any | None = ...,
        style: Any | None = ...,
        txBody: Any | None = ...,
    ) -> None: ...
