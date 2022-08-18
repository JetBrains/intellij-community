from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

from .colors import ColorChoice

class TintEffect(Serialisable):
    tagname: str
    hue: Any
    amt: Any
    def __init__(self, hue: int = ..., amt: int = ...) -> None: ...

class LuminanceEffect(Serialisable):
    tagname: str
    bright: Any
    contrast: Any
    def __init__(self, bright: int = ..., contrast: int = ...) -> None: ...

class HSLEffect(Serialisable):
    hue: Any
    sat: Any
    lum: Any
    def __init__(self, hue: Any | None = ..., sat: Any | None = ..., lum: Any | None = ...) -> None: ...

class GrayscaleEffect(Serialisable):
    tagname: str

class FillOverlayEffect(Serialisable):
    blend: Any
    def __init__(self, blend: Any | None = ...) -> None: ...

class DuotoneEffect(Serialisable): ...
class ColorReplaceEffect(Serialisable): ...
class Color(Serialisable): ...

class ColorChangeEffect(Serialisable):
    useA: Any
    clrFrom: Any
    clrTo: Any
    def __init__(self, useA: Any | None = ..., clrFrom: Any | None = ..., clrTo: Any | None = ...) -> None: ...

class BlurEffect(Serialisable):
    rad: Any
    grow: Any
    def __init__(self, rad: Any | None = ..., grow: Any | None = ...) -> None: ...

class BiLevelEffect(Serialisable):
    thresh: Any
    def __init__(self, thresh: Any | None = ...) -> None: ...

class AlphaReplaceEffect(Serialisable):
    a: Any
    def __init__(self, a: Any | None = ...) -> None: ...

class AlphaModulateFixedEffect(Serialisable):
    amt: Any
    def __init__(self, amt: Any | None = ...) -> None: ...

class EffectContainer(Serialisable):
    type: Any
    name: Any
    def __init__(self, type: Any | None = ..., name: Any | None = ...) -> None: ...

class AlphaModulateEffect(Serialisable):
    cont: Any
    def __init__(self, cont: Any | None = ...) -> None: ...

class AlphaInverseEffect(Serialisable): ...
class AlphaFloorEffect(Serialisable): ...
class AlphaCeilingEffect(Serialisable): ...

class AlphaBiLevelEffect(Serialisable):
    thresh: Any
    def __init__(self, thresh: Any | None = ...) -> None: ...

class GlowEffect(ColorChoice):
    rad: Any
    scrgbClr: Any
    srgbClr: Any
    hslClr: Any
    sysClr: Any
    schemeClr: Any
    prstClr: Any
    __elements__: Any
    def __init__(self, rad: Any | None = ..., **kw) -> None: ...

class InnerShadowEffect(ColorChoice):
    blurRad: Any
    dist: Any
    dir: Any
    scrgbClr: Any
    srgbClr: Any
    hslClr: Any
    sysClr: Any
    schemeClr: Any
    prstClr: Any
    __elements__: Any
    def __init__(self, blurRad: Any | None = ..., dist: Any | None = ..., dir: Any | None = ..., **kw) -> None: ...

class OuterShadow(ColorChoice):
    tagname: str
    blurRad: Any
    dist: Any
    dir: Any
    sx: Any
    sy: Any
    kx: Any
    ky: Any
    algn: Any
    rotWithShape: Any
    scrgbClr: Any
    srgbClr: Any
    hslClr: Any
    sysClr: Any
    schemeClr: Any
    prstClr: Any
    __elements__: Any
    def __init__(
        self,
        blurRad: Any | None = ...,
        dist: Any | None = ...,
        dir: Any | None = ...,
        sx: Any | None = ...,
        sy: Any | None = ...,
        kx: Any | None = ...,
        ky: Any | None = ...,
        algn: Any | None = ...,
        rotWithShape: Any | None = ...,
        **kw,
    ) -> None: ...

class PresetShadowEffect(ColorChoice):
    prst: Any
    dist: Any
    dir: Any
    scrgbClr: Any
    srgbClr: Any
    hslClr: Any
    sysClr: Any
    schemeClr: Any
    prstClr: Any
    __elements__: Any
    def __init__(self, prst: Any | None = ..., dist: Any | None = ..., dir: Any | None = ..., **kw) -> None: ...

class ReflectionEffect(Serialisable):
    blurRad: Any
    stA: Any
    stPos: Any
    endA: Any
    endPos: Any
    dist: Any
    dir: Any
    fadeDir: Any
    sx: Any
    sy: Any
    kx: Any
    ky: Any
    algn: Any
    rotWithShape: Any
    def __init__(
        self,
        blurRad: Any | None = ...,
        stA: Any | None = ...,
        stPos: Any | None = ...,
        endA: Any | None = ...,
        endPos: Any | None = ...,
        dist: Any | None = ...,
        dir: Any | None = ...,
        fadeDir: Any | None = ...,
        sx: Any | None = ...,
        sy: Any | None = ...,
        kx: Any | None = ...,
        ky: Any | None = ...,
        algn: Any | None = ...,
        rotWithShape: Any | None = ...,
    ) -> None: ...

class SoftEdgesEffect(Serialisable):
    rad: Any
    def __init__(self, rad: Any | None = ...) -> None: ...

class EffectList(Serialisable):
    blur: Any
    fillOverlay: Any
    glow: Any
    innerShdw: Any
    outerShdw: Any
    prstShdw: Any
    reflection: Any
    softEdge: Any
    __elements__: Any
    def __init__(
        self,
        blur: Any | None = ...,
        fillOverlay: Any | None = ...,
        glow: Any | None = ...,
        innerShdw: Any | None = ...,
        outerShdw: Any | None = ...,
        prstShdw: Any | None = ...,
        reflection: Any | None = ...,
        softEdge: Any | None = ...,
    ) -> None: ...
