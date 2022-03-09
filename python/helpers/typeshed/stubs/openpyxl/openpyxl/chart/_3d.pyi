from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class View3D(Serialisable):
    tagname: str
    rotX: Any
    x_rotation: Any
    hPercent: Any
    height_percent: Any
    rotY: Any
    y_rotation: Any
    depthPercent: Any
    rAngAx: Any
    right_angle_axes: Any
    perspective: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        rotX: int = ...,
        hPercent: Any | None = ...,
        rotY: int = ...,
        depthPercent: Any | None = ...,
        rAngAx: bool = ...,
        perspective: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class Surface(Serialisable):
    tagname: str
    thickness: Any
    spPr: Any
    graphicalProperties: Any
    pictureOptions: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self, thickness: Any | None = ..., spPr: Any | None = ..., pictureOptions: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...

class _3DBase(Serialisable):
    tagname: str
    view3D: Any
    floor: Any
    sideWall: Any
    backWall: Any
    def __init__(
        self, view3D: Any | None = ..., floor: Any | None = ..., sideWall: Any | None = ..., backWall: Any | None = ...
    ) -> None: ...
