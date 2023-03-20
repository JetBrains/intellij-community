from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class GroupShapeProperties(Serialisable):
    tagname: str
    bwMode: Any
    xfrm: Any
    scene3d: Any
    extLst: Any
    def __init__(
        self, bwMode: Any | None = ..., xfrm: Any | None = ..., scene3d: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...

class GroupLocking(Serialisable):
    tagname: str
    namespace: Any
    noGrp: Any
    noUngrp: Any
    noSelect: Any
    noRot: Any
    noChangeAspect: Any
    noMove: Any
    noResize: Any
    noChangeArrowheads: Any
    noEditPoints: Any
    noAdjustHandles: Any
    noChangeShapeType: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        noGrp: Any | None = ...,
        noUngrp: Any | None = ...,
        noSelect: Any | None = ...,
        noRot: Any | None = ...,
        noChangeAspect: Any | None = ...,
        noChangeArrowheads: Any | None = ...,
        noMove: Any | None = ...,
        noResize: Any | None = ...,
        noEditPoints: Any | None = ...,
        noAdjustHandles: Any | None = ...,
        noChangeShapeType: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class NonVisualGroupDrawingShapeProps(Serialisable):
    tagname: str
    grpSpLocks: Any
    extLst: Any
    __elements__: Any
    def __init__(self, grpSpLocks: Any | None = ..., extLst: Any | None = ...) -> None: ...

class NonVisualDrawingShapeProps(Serialisable):
    tagname: str
    spLocks: Any
    txBax: Any
    extLst: Any
    __elements__: Any
    txBox: Any
    def __init__(self, spLocks: Any | None = ..., txBox: Any | None = ..., extLst: Any | None = ...) -> None: ...

class NonVisualDrawingProps(Serialisable):
    tagname: str
    id: Any
    name: Any
    descr: Any
    hidden: Any
    title: Any
    hlinkClick: Any
    hlinkHover: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        id: Any | None = ...,
        name: Any | None = ...,
        descr: Any | None = ...,
        hidden: Any | None = ...,
        title: Any | None = ...,
        hlinkClick: Any | None = ...,
        hlinkHover: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class NonVisualGroupShape(Serialisable):
    tagname: str
    cNvPr: Any
    cNvGrpSpPr: Any
    __elements__: Any
    def __init__(self, cNvPr: Any | None = ..., cNvGrpSpPr: Any | None = ...) -> None: ...
