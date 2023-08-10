from typing import Any

from openpyxl.descriptors import Typed
from openpyxl.descriptors.serialisable import Serialisable

PRESET_COLORS: Any
SCHEME_COLORS: Any

class Transform(Serialisable): ...

class SystemColor(Serialisable):
    tagname: str
    namespace: Any
    tint: Any
    shade: Any
    comp: Any
    inv: Any
    gray: Any
    alpha: Any
    alphaOff: Any
    alphaMod: Any
    hue: Any
    hueOff: Any
    hueMod: Any
    sat: Any
    satOff: Any
    satMod: Any
    lum: Any
    lumOff: Any
    lumMod: Any
    red: Any
    redOff: Any
    redMod: Any
    green: Any
    greenOff: Any
    greenMod: Any
    blue: Any
    blueOff: Any
    blueMod: Any
    gamma: Any
    invGamma: Any
    val: Any
    lastClr: Any
    __elements__: Any
    def __init__(
        self,
        val: str = ...,
        lastClr: Any | None = ...,
        tint: Any | None = ...,
        shade: Any | None = ...,
        comp: Any | None = ...,
        inv: Any | None = ...,
        gray: Any | None = ...,
        alpha: Any | None = ...,
        alphaOff: Any | None = ...,
        alphaMod: Any | None = ...,
        hue: Any | None = ...,
        hueOff: Any | None = ...,
        hueMod: Any | None = ...,
        sat: Any | None = ...,
        satOff: Any | None = ...,
        satMod: Any | None = ...,
        lum: Any | None = ...,
        lumOff: Any | None = ...,
        lumMod: Any | None = ...,
        red: Any | None = ...,
        redOff: Any | None = ...,
        redMod: Any | None = ...,
        green: Any | None = ...,
        greenOff: Any | None = ...,
        greenMod: Any | None = ...,
        blue: Any | None = ...,
        blueOff: Any | None = ...,
        blueMod: Any | None = ...,
        gamma: Any | None = ...,
        invGamma: Any | None = ...,
    ) -> None: ...

class HSLColor(Serialisable):
    tagname: str
    hue: Any
    sat: Any
    lum: Any
    def __init__(self, hue: Any | None = ..., sat: Any | None = ..., lum: Any | None = ...) -> None: ...

class RGBPercent(Serialisable):
    tagname: str
    r: Any
    g: Any
    b: Any
    def __init__(self, r: Any | None = ..., g: Any | None = ..., b: Any | None = ...) -> None: ...

class SchemeColor(Serialisable):
    tagname: str
    namespace: Any
    tint: Any
    shade: Any
    comp: Any
    inv: Any
    gray: Any
    alpha: Any
    alphaOff: Any
    alphaMod: Any
    hue: Any
    hueOff: Any
    hueMod: Any
    sat: Any
    satOff: Any
    satMod: Any
    lum: Any
    lumOff: Any
    lumMod: Any
    red: Any
    redOff: Any
    redMod: Any
    green: Any
    greenOff: Any
    greenMod: Any
    blue: Any
    blueOff: Any
    blueMod: Any
    gamma: Any
    invGamma: Any
    val: Any
    __elements__: Any
    def __init__(
        self,
        tint: Any | None = ...,
        shade: Any | None = ...,
        comp: Any | None = ...,
        inv: Any | None = ...,
        gray: Any | None = ...,
        alpha: Any | None = ...,
        alphaOff: Any | None = ...,
        alphaMod: Any | None = ...,
        hue: Any | None = ...,
        hueOff: Any | None = ...,
        hueMod: Any | None = ...,
        sat: Any | None = ...,
        satOff: Any | None = ...,
        satMod: Any | None = ...,
        lum: Any | None = ...,
        lumOff: Any | None = ...,
        lumMod: Any | None = ...,
        red: Any | None = ...,
        redOff: Any | None = ...,
        redMod: Any | None = ...,
        green: Any | None = ...,
        greenOff: Any | None = ...,
        greenMod: Any | None = ...,
        blue: Any | None = ...,
        blueOff: Any | None = ...,
        blueMod: Any | None = ...,
        gamma: Any | None = ...,
        invGamma: Any | None = ...,
        val: Any | None = ...,
    ) -> None: ...

class ColorChoice(Serialisable):
    tagname: str
    namespace: Any
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

class ColorMapping(Serialisable):
    tagname: str
    bg1: Any
    tx1: Any
    bg2: Any
    tx2: Any
    accent1: Any
    accent2: Any
    accent3: Any
    accent4: Any
    accent5: Any
    accent6: Any
    hlink: Any
    folHlink: Any
    extLst: Any
    def __init__(
        self,
        bg1: str = ...,
        tx1: str = ...,
        bg2: str = ...,
        tx2: str = ...,
        accent1: str = ...,
        accent2: str = ...,
        accent3: str = ...,
        accent4: str = ...,
        accent5: str = ...,
        accent6: str = ...,
        hlink: str = ...,
        folHlink: str = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class ColorChoiceDescriptor(Typed):
    expected_type: Any
    allow_none: bool
    def __set__(self, instance, value) -> None: ...
