from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class GraphicFrameLocking(Serialisable):
    noGrp: Any
    noDrilldown: Any
    noSelect: Any
    noChangeAspect: Any
    noMove: Any
    noResize: Any
    extLst: Any
    def __init__(
        self,
        noGrp: Any | None = ...,
        noDrilldown: Any | None = ...,
        noSelect: Any | None = ...,
        noChangeAspect: Any | None = ...,
        noMove: Any | None = ...,
        noResize: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class NonVisualGraphicFrameProperties(Serialisable):
    tagname: str
    graphicFrameLocks: Any
    extLst: Any
    def __init__(self, graphicFrameLocks: Any | None = ..., extLst: Any | None = ...) -> None: ...

class NonVisualGraphicFrame(Serialisable):
    tagname: str
    cNvPr: Any
    cNvGraphicFramePr: Any
    __elements__: Any
    def __init__(self, cNvPr: Any | None = ..., cNvGraphicFramePr: Any | None = ...) -> None: ...

class GraphicData(Serialisable):
    tagname: str
    namespace: Any
    uri: Any
    chart: Any
    def __init__(self, uri=..., chart: Any | None = ...) -> None: ...

class GraphicObject(Serialisable):
    tagname: str
    namespace: Any
    graphicData: Any
    def __init__(self, graphicData: Any | None = ...) -> None: ...

class GraphicFrame(Serialisable):
    tagname: str
    nvGraphicFramePr: Any
    xfrm: Any
    graphic: Any
    macro: Any
    fPublished: Any
    __elements__: Any
    def __init__(
        self,
        nvGraphicFramePr: Any | None = ...,
        xfrm: Any | None = ...,
        graphic: Any | None = ...,
        macro: Any | None = ...,
        fPublished: Any | None = ...,
    ) -> None: ...

class GroupShape(Serialisable):
    nvGrpSpPr: Any
    nonVisualProperties: Any
    grpSpPr: Any
    visualProperties: Any
    pic: Any
    __elements__: Any
    def __init__(self, nvGrpSpPr: Any | None = ..., grpSpPr: Any | None = ..., pic: Any | None = ...) -> None: ...
