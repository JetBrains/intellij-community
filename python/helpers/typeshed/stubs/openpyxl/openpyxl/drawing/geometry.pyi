from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Point2D(Serialisable):
    tagname: str
    namespace: Any
    x: Any
    y: Any
    def __init__(self, x: Any | None = ..., y: Any | None = ...) -> None: ...

class PositiveSize2D(Serialisable):
    tagname: str
    namespace: Any
    cx: Any
    width: Any
    cy: Any
    height: Any
    def __init__(self, cx: Any | None = ..., cy: Any | None = ...) -> None: ...

class Transform2D(Serialisable):
    tagname: str
    namespace: Any
    rot: Any
    flipH: Any
    flipV: Any
    off: Any
    ext: Any
    chOff: Any
    chExt: Any
    __elements__: Any
    def __init__(
        self,
        rot: Any | None = ...,
        flipH: Any | None = ...,
        flipV: Any | None = ...,
        off: Any | None = ...,
        ext: Any | None = ...,
        chOff: Any | None = ...,
        chExt: Any | None = ...,
    ) -> None: ...

class GroupTransform2D(Serialisable):
    tagname: str
    namespace: Any
    rot: Any
    flipH: Any
    flipV: Any
    off: Any
    ext: Any
    chOff: Any
    chExt: Any
    __elements__: Any
    def __init__(
        self,
        rot: int = ...,
        flipH: Any | None = ...,
        flipV: Any | None = ...,
        off: Any | None = ...,
        ext: Any | None = ...,
        chOff: Any | None = ...,
        chExt: Any | None = ...,
    ) -> None: ...

class SphereCoords(Serialisable):
    tagname: str
    lat: Any
    lon: Any
    rev: Any
    def __init__(self, lat: Any | None = ..., lon: Any | None = ..., rev: Any | None = ...) -> None: ...

class Camera(Serialisable):
    tagname: str
    prst: Any
    fov: Any
    zoom: Any
    rot: Any
    def __init__(self, prst: Any | None = ..., fov: Any | None = ..., zoom: Any | None = ..., rot: Any | None = ...) -> None: ...

class LightRig(Serialisable):
    tagname: str
    rig: Any
    dir: Any
    rot: Any
    def __init__(self, rig: Any | None = ..., dir: Any | None = ..., rot: Any | None = ...) -> None: ...

class Vector3D(Serialisable):
    tagname: str
    dx: Any
    dy: Any
    dz: Any
    def __init__(self, dx: Any | None = ..., dy: Any | None = ..., dz: Any | None = ...) -> None: ...

class Point3D(Serialisable):
    tagname: str
    x: Any
    y: Any
    z: Any
    def __init__(self, x: Any | None = ..., y: Any | None = ..., z: Any | None = ...) -> None: ...

class Backdrop(Serialisable):
    anchor: Any
    norm: Any
    up: Any
    extLst: Any
    def __init__(
        self, anchor: Any | None = ..., norm: Any | None = ..., up: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...

class Scene3D(Serialisable):
    camera: Any
    lightRig: Any
    backdrop: Any
    extLst: Any
    def __init__(
        self, camera: Any | None = ..., lightRig: Any | None = ..., backdrop: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...

class Bevel(Serialisable):
    tagname: str
    w: Any
    h: Any
    prst: Any
    def __init__(self, w: Any | None = ..., h: Any | None = ..., prst: Any | None = ...) -> None: ...

class Shape3D(Serialisable):
    namespace: Any
    z: Any
    extrusionH: Any
    contourW: Any
    prstMaterial: Any
    bevelT: Any
    bevelB: Any
    extrusionClr: Any
    contourClr: Any
    extLst: Any
    def __init__(
        self,
        z: Any | None = ...,
        extrusionH: Any | None = ...,
        contourW: Any | None = ...,
        prstMaterial: Any | None = ...,
        bevelT: Any | None = ...,
        bevelB: Any | None = ...,
        extrusionClr: Any | None = ...,
        contourClr: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class Path2D(Serialisable):
    w: Any
    h: Any
    fill: Any
    stroke: Any
    extrusionOk: Any
    def __init__(
        self,
        w: Any | None = ...,
        h: Any | None = ...,
        fill: Any | None = ...,
        stroke: Any | None = ...,
        extrusionOk: Any | None = ...,
    ) -> None: ...

class Path2DList(Serialisable):
    path: Any
    def __init__(self, path: Any | None = ...) -> None: ...

class GeomRect(Serialisable):
    l: Any
    t: Any
    r: Any
    b: Any
    def __init__(self, l: Any | None = ..., t: Any | None = ..., r: Any | None = ..., b: Any | None = ...) -> None: ...

class AdjPoint2D(Serialisable):
    x: Any
    y: Any
    def __init__(self, x: Any | None = ..., y: Any | None = ...) -> None: ...

class ConnectionSite(Serialisable):
    ang: Any
    pos: Any
    def __init__(self, ang: Any | None = ..., pos: Any | None = ...) -> None: ...

class ConnectionSiteList(Serialisable):
    cxn: Any
    def __init__(self, cxn: Any | None = ...) -> None: ...

class AdjustHandleList(Serialisable): ...

class GeomGuide(Serialisable):
    name: Any
    fmla: Any
    def __init__(self, name: Any | None = ..., fmla: Any | None = ...) -> None: ...

class GeomGuideList(Serialisable):
    gd: Any
    def __init__(self, gd: Any | None = ...) -> None: ...

class CustomGeometry2D(Serialisable):
    avLst: Any
    gdLst: Any
    ahLst: Any
    cxnLst: Any
    pathLst: Any
    rect: Any
    def __init__(
        self,
        avLst: Any | None = ...,
        gdLst: Any | None = ...,
        ahLst: Any | None = ...,
        cxnLst: Any | None = ...,
        rect: Any | None = ...,
        pathLst: Any | None = ...,
    ) -> None: ...

class PresetGeometry2D(Serialisable):
    namespace: Any
    prst: Any
    avLst: Any
    def __init__(self, prst: Any | None = ..., avLst: Any | None = ...) -> None: ...

class FontReference(Serialisable):
    idx: Any
    def __init__(self, idx: Any | None = ...) -> None: ...

class StyleMatrixReference(Serialisable):
    idx: Any
    def __init__(self, idx: Any | None = ...) -> None: ...

class ShapeStyle(Serialisable):
    lnRef: Any
    fillRef: Any
    effectRef: Any
    fontRef: Any
    def __init__(
        self, lnRef: Any | None = ..., fillRef: Any | None = ..., effectRef: Any | None = ..., fontRef: Any | None = ...
    ) -> None: ...
