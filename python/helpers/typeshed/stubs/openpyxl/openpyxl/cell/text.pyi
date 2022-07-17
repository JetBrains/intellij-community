from typing import Any

from openpyxl.descriptors.serialisable import Serialisable
from openpyxl.styles.fonts import Font

class PhoneticProperties(Serialisable):
    tagname: str
    fontId: Any
    type: Any
    alignment: Any
    def __init__(self, fontId: Any | None = ..., type: Any | None = ..., alignment: Any | None = ...) -> None: ...

class PhoneticText(Serialisable):
    tagname: str
    sb: Any
    eb: Any
    t: Any
    text: Any
    def __init__(self, sb: Any | None = ..., eb: Any | None = ..., t: Any | None = ...) -> None: ...

class InlineFont(Font):
    tagname: str
    rFont: Any
    charset: Any
    family: Any
    b: Any
    i: Any
    strike: Any
    outline: Any
    shadow: Any
    condense: Any
    extend: Any
    color: Any
    sz: Any
    u: Any
    vertAlign: Any
    scheme: Any
    __elements__: Any
    def __init__(
        self,
        rFont: Any | None = ...,
        charset: Any | None = ...,
        family: Any | None = ...,
        b: Any | None = ...,
        i: Any | None = ...,
        strike: Any | None = ...,
        outline: Any | None = ...,
        shadow: Any | None = ...,
        condense: Any | None = ...,
        extend: Any | None = ...,
        color: Any | None = ...,
        sz: Any | None = ...,
        u: Any | None = ...,
        vertAlign: Any | None = ...,
        scheme: Any | None = ...,
    ) -> None: ...

class RichText(Serialisable):
    tagname: str
    rPr: Any
    font: Any
    t: Any
    text: Any
    __elements__: Any
    def __init__(self, rPr: Any | None = ..., t: Any | None = ...) -> None: ...

class Text(Serialisable):
    tagname: str
    t: Any
    plain: Any
    r: Any
    formatted: Any
    rPh: Any
    phonetic: Any
    phoneticPr: Any
    PhoneticProperties: Any
    __elements__: Any
    def __init__(self, t: Any | None = ..., r=..., rPh=..., phoneticPr: Any | None = ...) -> None: ...
    @property
    def content(self): ...
