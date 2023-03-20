from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class ObjectAnchor(Serialisable):
    tagname: str
    to: Any
    moveWithCells: Any
    sizeWithCells: Any
    z_order: Any
    def __init__(
        self,
        _from: Any | None = ...,
        to: Any | None = ...,
        moveWithCells: bool = ...,
        sizeWithCells: bool = ...,
        z_order: Any | None = ...,
    ) -> None: ...

class ObjectPr(Serialisable):
    tagname: str
    anchor: Any
    locked: Any
    defaultSize: Any
    disabled: Any
    uiObject: Any
    autoFill: Any
    autoLine: Any
    autoPict: Any
    macro: Any
    altText: Any
    dde: Any
    __elements__: Any
    def __init__(
        self,
        anchor: Any | None = ...,
        locked: bool = ...,
        defaultSize: bool = ...,
        _print: bool = ...,
        disabled: bool = ...,
        uiObject: bool = ...,
        autoFill: bool = ...,
        autoLine: bool = ...,
        autoPict: bool = ...,
        macro: Any | None = ...,
        altText: Any | None = ...,
        dde: bool = ...,
    ) -> None: ...

class OleObject(Serialisable):
    tagname: str
    objectPr: Any
    progId: Any
    dvAspect: Any
    link: Any
    oleUpdate: Any
    autoLoad: Any
    shapeId: Any
    __elements__: Any
    def __init__(
        self,
        objectPr: Any | None = ...,
        progId: Any | None = ...,
        dvAspect: str = ...,
        link: Any | None = ...,
        oleUpdate: Any | None = ...,
        autoLoad: bool = ...,
        shapeId: Any | None = ...,
    ) -> None: ...

class OleObjects(Serialisable):
    tagname: str
    oleObject: Any
    __elements__: Any
    def __init__(self, oleObject=...) -> None: ...
