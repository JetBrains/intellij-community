from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class GraphicalProperties(Serialisable):
    tagname: str
    bwMode: Any
    xfrm: Any
    transform: Any
    custGeom: Any
    prstGeom: Any
    noFill: Any
    solidFill: Any
    gradFill: Any
    pattFill: Any
    ln: Any
    line: Any
    scene3d: Any
    sp3d: Any
    shape3D: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        bwMode: Any | None = ...,
        xfrm: Any | None = ...,
        noFill: Any | None = ...,
        solidFill: Any | None = ...,
        gradFill: Any | None = ...,
        pattFill: Any | None = ...,
        ln: Any | None = ...,
        scene3d: Any | None = ...,
        custGeom: Any | None = ...,
        prstGeom: Any | None = ...,
        sp3d: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
