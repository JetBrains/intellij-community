from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class PatternFillProperties(Serialisable):
    tagname: str
    namespace: Any
    prst: Any
    preset: Any
    fgClr: Any
    foreground: Any
    bgClr: Any
    background: Any
    __elements__: Any
    def __init__(self, prst: Any | None = ..., fgClr: Any | None = ..., bgClr: Any | None = ...) -> None: ...

class RelativeRect(Serialisable):
    tagname: str
    namespace: Any
    l: Any
    left: Any
    t: Any
    top: Any
    r: Any
    right: Any
    b: Any
    bottom: Any
    def __init__(self, l: Any | None = ..., t: Any | None = ..., r: Any | None = ..., b: Any | None = ...) -> None: ...

class StretchInfoProperties(Serialisable):
    tagname: str
    namespace: Any
    fillRect: Any
    def __init__(self, fillRect=...) -> None: ...

class GradientStop(Serialisable):
    tagname: str
    namespace: Any
    pos: Any
    scrgbClr: Any
    RGBPercent: Any
    srgbClr: Any
    RGB: Any
    hslClr: Any
    sysClr: Any
    schemeClr: Any
    prstClr: Any
    __elements__: Any
    def __init__(
        self,
        pos: Any | None = ...,
        scrgbClr: Any | None = ...,
        srgbClr: Any | None = ...,
        hslClr: Any | None = ...,
        sysClr: Any | None = ...,
        schemeClr: Any | None = ...,
        prstClr: Any | None = ...,
    ) -> None: ...

class LinearShadeProperties(Serialisable):
    tagname: str
    namespace: Any
    ang: Any
    scaled: Any
    def __init__(self, ang: Any | None = ..., scaled: Any | None = ...) -> None: ...

class PathShadeProperties(Serialisable):
    tagname: str
    namespace: Any
    path: Any
    fillToRect: Any
    def __init__(self, path: Any | None = ..., fillToRect: Any | None = ...) -> None: ...

class GradientFillProperties(Serialisable):
    tagname: str
    namespace: Any
    flip: Any
    rotWithShape: Any
    gsLst: Any
    stop_list: Any
    lin: Any
    linear: Any
    path: Any
    tileRect: Any
    __elements__: Any
    def __init__(
        self,
        flip: Any | None = ...,
        rotWithShape: Any | None = ...,
        gsLst=...,
        lin: Any | None = ...,
        path: Any | None = ...,
        tileRect: Any | None = ...,
    ) -> None: ...

class SolidColorFillProperties(Serialisable):
    tagname: str
    scrgbClr: Any
    RGBPercent: Any
    srgbClr: Any
    RGB: Any
    hslClr: Any
    sysClr: Any
    schemeClr: Any
    prstClr: Any
    __elements__: Any
    def __init__(
        self,
        scrgbClr: Any | None = ...,
        srgbClr: Any | None = ...,
        hslClr: Any | None = ...,
        sysClr: Any | None = ...,
        schemeClr: Any | None = ...,
        prstClr: Any | None = ...,
    ) -> None: ...

class Blip(Serialisable):
    tagname: str
    namespace: Any
    cstate: Any
    embed: Any
    link: Any
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
    alphaBiLevel: Any
    alphaCeiling: Any
    alphaFloor: Any
    alphaInv: Any
    alphaMod: Any
    alphaModFix: Any
    alphaRepl: Any
    biLevel: Any
    blur: Any
    clrChange: Any
    clrRepl: Any
    duotone: Any
    fillOverlay: Any
    grayscl: Any
    hsl: Any
    lum: Any
    tint: Any
    __elements__: Any
    def __init__(
        self,
        cstate: Any | None = ...,
        embed: Any | None = ...,
        link: Any | None = ...,
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
        alphaBiLevel: Any | None = ...,
        alphaCeiling: Any | None = ...,
        alphaFloor: Any | None = ...,
        alphaInv: Any | None = ...,
        alphaMod: Any | None = ...,
        alphaModFix: Any | None = ...,
        alphaRepl: Any | None = ...,
        biLevel: Any | None = ...,
        blur: Any | None = ...,
        clrChange: Any | None = ...,
        clrRepl: Any | None = ...,
        duotone: Any | None = ...,
        fillOverlay: Any | None = ...,
        grayscl: Any | None = ...,
        hsl: Any | None = ...,
        lum: Any | None = ...,
        tint: Any | None = ...,
    ) -> None: ...

class TileInfoProperties(Serialisable):
    tx: Any
    ty: Any
    sx: Any
    sy: Any
    flip: Any
    algn: Any
    def __init__(
        self,
        tx: Any | None = ...,
        ty: Any | None = ...,
        sx: Any | None = ...,
        sy: Any | None = ...,
        flip: Any | None = ...,
        algn: Any | None = ...,
    ) -> None: ...

class BlipFillProperties(Serialisable):
    tagname: str
    dpi: Any
    rotWithShape: Any
    blip: Any
    srcRect: Any
    tile: Any
    stretch: Any
    __elements__: Any
    def __init__(
        self,
        dpi: Any | None = ...,
        rotWithShape: Any | None = ...,
        blip: Any | None = ...,
        tile: Any | None = ...,
        stretch=...,
        srcRect: Any | None = ...,
    ) -> None: ...
