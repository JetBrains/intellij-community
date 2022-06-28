from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class PictureLocking(Serialisable):
    tagname: str
    namespace: Any
    noCrop: Any
    noGrp: Any
    noSelect: Any
    noRot: Any
    noChangeAspect: Any
    noMove: Any
    noResize: Any
    noEditPoints: Any
    noAdjustHandles: Any
    noChangeArrowheads: Any
    noChangeShapeType: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        noCrop: Any | None = ...,
        noGrp: Any | None = ...,
        noSelect: Any | None = ...,
        noRot: Any | None = ...,
        noChangeAspect: Any | None = ...,
        noMove: Any | None = ...,
        noResize: Any | None = ...,
        noEditPoints: Any | None = ...,
        noAdjustHandles: Any | None = ...,
        noChangeArrowheads: Any | None = ...,
        noChangeShapeType: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class NonVisualPictureProperties(Serialisable):
    tagname: str
    preferRelativeResize: Any
    picLocks: Any
    extLst: Any
    __elements__: Any
    def __init__(self, preferRelativeResize: Any | None = ..., picLocks: Any | None = ..., extLst: Any | None = ...) -> None: ...

class PictureNonVisual(Serialisable):
    tagname: str
    cNvPr: Any
    cNvPicPr: Any
    __elements__: Any
    def __init__(self, cNvPr: Any | None = ..., cNvPicPr: Any | None = ...) -> None: ...

class PictureFrame(Serialisable):
    tagname: str
    macro: Any
    fPublished: Any
    nvPicPr: Any
    blipFill: Any
    spPr: Any
    graphicalProperties: Any
    style: Any
    __elements__: Any
    def __init__(
        self,
        macro: Any | None = ...,
        fPublished: Any | None = ...,
        nvPicPr: Any | None = ...,
        blipFill: Any | None = ...,
        spPr: Any | None = ...,
        style: Any | None = ...,
    ) -> None: ...
