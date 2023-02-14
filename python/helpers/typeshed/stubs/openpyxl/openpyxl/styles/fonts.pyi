from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Font(Serialisable):
    UNDERLINE_DOUBLE: str
    UNDERLINE_DOUBLE_ACCOUNTING: str
    UNDERLINE_SINGLE: str
    UNDERLINE_SINGLE_ACCOUNTING: str
    name: Any
    charset: Any
    family: Any
    sz: Any
    size: Any
    b: Any
    bold: Any
    i: Any
    italic: Any
    strike: Any
    strikethrough: Any
    outline: Any
    shadow: Any
    condense: Any
    extend: Any
    u: Any
    underline: Any
    vertAlign: Any
    color: Any
    scheme: Any
    tagname: str
    __elements__: Any
    def __init__(
        self,
        name: Any | None = ...,
        sz: Any | None = ...,
        b: Any | None = ...,
        i: Any | None = ...,
        charset: Any | None = ...,
        u: Any | None = ...,
        strike: Any | None = ...,
        color: Any | None = ...,
        scheme: Any | None = ...,
        family: Any | None = ...,
        size: Any | None = ...,
        bold: Any | None = ...,
        italic: Any | None = ...,
        strikethrough: Any | None = ...,
        underline: Any | None = ...,
        vertAlign: Any | None = ...,
        outline: Any | None = ...,
        shadow: Any | None = ...,
        condense: Any | None = ...,
        extend: Any | None = ...,
    ) -> None: ...
    @classmethod
    def from_tree(cls, node): ...

DEFAULT_FONT: Any
