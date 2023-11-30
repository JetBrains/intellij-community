from typing import ClassVar, overload
from typing_extensions import Literal, TypeAlias

from openpyxl.descriptors.base import (
    Bool,
    Float,
    Integer,
    Set,
    String,
    Typed,
    _ConvertibleToBool,
    _ConvertibleToFloat,
    _ConvertibleToInt,
)
from openpyxl.descriptors.serialisable import Serialisable

from .colors import ColorChoice

_FillOverlayEffectBlend: TypeAlias = Literal["over", "mult", "screen", "darken", "lighten"]
_EffectContainerType: TypeAlias = Literal["sib", "tree"]
_Algn: TypeAlias = Literal["tl", "t", "tr", "l", "ctr", "r", "bl", "b", "br"]
_PresetShadowEffectPrst: TypeAlias = Literal[
    "shdw1",
    "shdw2",
    "shdw3",
    "shdw4",
    "shdw5",
    "shdw6",
    "shdw7",
    "shdw8",
    "shdw9",
    "shdw10",
    "shdw11",
    "shdw12",
    "shdw13",
    "shdw14",
    "shdw15",
    "shdw16",
    "shdw17",
    "shdw18",
    "shdw19",
    "shdw20",
]

class TintEffect(Serialisable):
    tagname: ClassVar[str]
    hue: Integer[Literal[False]]
    amt: Integer[Literal[False]]
    def __init__(self, hue: _ConvertibleToInt = 0, amt: _ConvertibleToInt = 0) -> None: ...

class LuminanceEffect(Serialisable):
    tagname: ClassVar[str]
    bright: Integer[Literal[False]]
    contrast: Integer[Literal[False]]
    def __init__(self, bright: _ConvertibleToInt = 0, contrast: _ConvertibleToInt = 0) -> None: ...

class HSLEffect(Serialisable):
    hue: Integer[Literal[False]]
    sat: Integer[Literal[False]]
    lum: Integer[Literal[False]]
    def __init__(self, hue: _ConvertibleToInt, sat: _ConvertibleToInt, lum: _ConvertibleToInt) -> None: ...

class GrayscaleEffect(Serialisable):
    tagname: ClassVar[str]

class FillOverlayEffect(Serialisable):
    blend: Set[_FillOverlayEffectBlend]
    def __init__(self, blend: _FillOverlayEffectBlend) -> None: ...

class DuotoneEffect(Serialisable): ...
class ColorReplaceEffect(Serialisable): ...
class Color(Serialisable): ...

class ColorChangeEffect(Serialisable):
    useA: Bool[Literal[True]]
    clrFrom: Typed[Color, Literal[False]]
    clrTo: Typed[Color, Literal[False]]
    @overload
    def __init__(self, useA: _ConvertibleToBool | None = None, *, clrFrom: Color, clrTo: Color) -> None: ...
    @overload
    def __init__(self, useA: _ConvertibleToBool | None, clrFrom: Color, clrTo: Color) -> None: ...

class BlurEffect(Serialisable):
    rad: Float[Literal[False]]
    grow: Bool[Literal[True]]
    def __init__(self, rad: _ConvertibleToFloat, grow: _ConvertibleToBool | None = None) -> None: ...

class BiLevelEffect(Serialisable):
    thresh: Integer[Literal[False]]
    def __init__(self, thresh: _ConvertibleToInt) -> None: ...

class AlphaReplaceEffect(Serialisable):
    a: Integer[Literal[False]]
    def __init__(self, a: _ConvertibleToInt) -> None: ...

class AlphaModulateFixedEffect(Serialisable):
    amt: Integer[Literal[False]]
    def __init__(self, amt: _ConvertibleToInt) -> None: ...

class EffectContainer(Serialisable):
    type: Set[_EffectContainerType]
    name: String[Literal[True]]
    def __init__(self, type: _EffectContainerType, name: str | None = None) -> None: ...

class AlphaModulateEffect(Serialisable):
    cont: Typed[EffectContainer, Literal[False]]
    def __init__(self, cont: EffectContainer) -> None: ...

class AlphaInverseEffect(Serialisable): ...
class AlphaFloorEffect(Serialisable): ...
class AlphaCeilingEffect(Serialisable): ...

class AlphaBiLevelEffect(Serialisable):
    thresh: Integer[Literal[False]]
    def __init__(self, thresh: _ConvertibleToInt) -> None: ...

class GlowEffect(ColorChoice):
    rad: Float[Literal[False]]
    # Same as parent
    # scrgbClr = ColorChoice.scrgbClr
    # srgbClr = ColorChoice.srgbClr
    # hslClr = ColorChoice.hslClr
    # sysClr = ColorChoice.sysClr
    # schemeClr = ColorChoice.schemeClr
    # prstClr = ColorChoice.prstClr
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(self, rad: _ConvertibleToFloat, **kw) -> None: ...

class InnerShadowEffect(ColorChoice):
    blurRad: Float[Literal[False]]
    dist: Float[Literal[False]]
    dir: Integer[Literal[False]]
    # Same as parent
    # scrgbClr = ColorChoice.scrgbClr
    # srgbClr = ColorChoice.srgbClr
    # hslClr = ColorChoice.hslClr
    # sysClr = ColorChoice.sysClr
    # schemeClr = ColorChoice.schemeClr
    # prstClr = ColorChoice.prstClr
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(self, blurRad: _ConvertibleToFloat, dist: _ConvertibleToFloat, dir: _ConvertibleToInt, **kw) -> None: ...

class OuterShadow(ColorChoice):
    tagname: ClassVar[str]
    blurRad: Float[Literal[True]]
    dist: Float[Literal[True]]
    dir: Integer[Literal[True]]
    sx: Integer[Literal[True]]
    sy: Integer[Literal[True]]
    kx: Integer[Literal[True]]
    ky: Integer[Literal[True]]
    algn: Set[_Algn]
    rotWithShape: Bool[Literal[True]]
    # Same as parent
    # scrgbClr = ColorChoice.scrgbClr
    # srgbClr = ColorChoice.srgbClr
    # hslClr = ColorChoice.hslClr
    # sysClr = ColorChoice.sysClr
    # schemeClr = ColorChoice.schemeClr
    # prstClr = ColorChoice.prstClr
    __elements__: ClassVar[tuple[str, ...]]
    @overload
    def __init__(
        self,
        blurRad: _ConvertibleToFloat | None = None,
        dist: _ConvertibleToFloat | None = None,
        dir: _ConvertibleToInt | None = None,
        sx: _ConvertibleToInt | None = None,
        sy: _ConvertibleToInt | None = None,
        kx: _ConvertibleToInt | None = None,
        ky: _ConvertibleToInt | None = None,
        *,
        algn: _Algn,
        rotWithShape: _ConvertibleToBool | None = None,
        **kw,
    ) -> None: ...
    @overload
    def __init__(
        self,
        blurRad: _ConvertibleToFloat | None,
        dist: _ConvertibleToFloat | None,
        dir: _ConvertibleToInt | None,
        sx: _ConvertibleToInt | None,
        sy: _ConvertibleToInt | None,
        kx: _ConvertibleToInt | None,
        ky: _ConvertibleToInt | None,
        algn: _Algn,
        rotWithShape: _ConvertibleToBool | None = None,
        **kw,
    ) -> None: ...

class PresetShadowEffect(ColorChoice):
    prst: Set[_PresetShadowEffectPrst]
    dist: Float[Literal[False]]
    dir: Integer[Literal[False]]
    # Same as parent
    # scrgbClr = ColorChoice.scrgbClr
    # srgbClr = ColorChoice.srgbClr
    # hslClr = ColorChoice.hslClr
    # sysClr = ColorChoice.sysClr
    # schemeClr = ColorChoice.schemeClr
    # prstClr = ColorChoice.prstClr
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(self, prst: _PresetShadowEffectPrst, dist: _ConvertibleToFloat, dir: _ConvertibleToInt, **kw) -> None: ...

class ReflectionEffect(Serialisable):
    blurRad: Float[Literal[False]]
    stA: Integer[Literal[False]]
    stPos: Integer[Literal[False]]
    endA: Integer[Literal[False]]
    endPos: Integer[Literal[False]]
    dist: Float[Literal[False]]
    dir: Integer[Literal[False]]
    fadeDir: Integer[Literal[False]]
    sx: Integer[Literal[False]]
    sy: Integer[Literal[False]]
    kx: Integer[Literal[False]]
    ky: Integer[Literal[False]]
    algn: Set[_Algn]
    rotWithShape: Bool[Literal[True]]
    def __init__(
        self,
        blurRad: _ConvertibleToFloat,
        stA: _ConvertibleToInt,
        stPos: _ConvertibleToInt,
        endA: _ConvertibleToInt,
        endPos: _ConvertibleToInt,
        dist: _ConvertibleToFloat,
        dir: _ConvertibleToInt,
        fadeDir: _ConvertibleToInt,
        sx: _ConvertibleToInt,
        sy: _ConvertibleToInt,
        kx: _ConvertibleToInt,
        ky: _ConvertibleToInt,
        algn: _Algn,
        rotWithShape: _ConvertibleToBool | None = None,
    ) -> None: ...

class SoftEdgesEffect(Serialisable):
    rad: Float[Literal[False]]
    def __init__(self, rad: _ConvertibleToFloat) -> None: ...

class EffectList(Serialisable):
    blur: Typed[BlurEffect, Literal[True]]
    fillOverlay: Typed[FillOverlayEffect, Literal[True]]
    glow: Typed[GlowEffect, Literal[True]]
    innerShdw: Typed[InnerShadowEffect, Literal[True]]
    outerShdw: Typed[OuterShadow, Literal[True]]
    prstShdw: Typed[PresetShadowEffect, Literal[True]]
    reflection: Typed[ReflectionEffect, Literal[True]]
    softEdge: Typed[SoftEdgesEffect, Literal[True]]
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(
        self,
        blur: BlurEffect | None = None,
        fillOverlay: FillOverlayEffect | None = None,
        glow: GlowEffect | None = None,
        innerShdw: InnerShadowEffect | None = None,
        outerShdw: OuterShadow | None = None,
        prstShdw: PresetShadowEffect | None = None,
        reflection: ReflectionEffect | None = None,
        softEdge: SoftEdgesEffect | None = None,
    ) -> None: ...
