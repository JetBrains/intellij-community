from typing import Any

from openpyxl.descriptors import Strict
from openpyxl.descriptors.serialisable import Serialisable

FONT_PATTERN: str
COLOR_PATTERN: str
SIZE_REGEX: str
FORMAT_REGEX: Any

class _HeaderFooterPart(Strict):
    text: Any
    font: Any
    size: Any
    RGB: str
    color: Any
    def __init__(
        self, text: Any | None = ..., font: Any | None = ..., size: Any | None = ..., color: Any | None = ...
    ) -> None: ...
    def __bool__(self): ...
    @classmethod
    def from_str(cls, text): ...

class HeaderFooterItem(Strict):
    left: Any
    center: Any
    centre: Any
    right: Any
    def __init__(self, left: Any | None = ..., right: Any | None = ..., center: Any | None = ...) -> None: ...
    def __bool__(self): ...
    def to_tree(self, tagname): ...
    @classmethod
    def from_tree(cls, node): ...

class HeaderFooter(Serialisable):
    tagname: str
    differentOddEven: Any
    differentFirst: Any
    scaleWithDoc: Any
    alignWithMargins: Any
    oddHeader: Any
    oddFooter: Any
    evenHeader: Any
    evenFooter: Any
    firstHeader: Any
    firstFooter: Any
    __elements__: Any
    def __init__(
        self,
        differentOddEven: Any | None = ...,
        differentFirst: Any | None = ...,
        scaleWithDoc: Any | None = ...,
        alignWithMargins: Any | None = ...,
        oddHeader: Any | None = ...,
        oddFooter: Any | None = ...,
        evenHeader: Any | None = ...,
        evenFooter: Any | None = ...,
        firstHeader: Any | None = ...,
        firstFooter: Any | None = ...,
    ) -> None: ...
    def __bool__(self): ...
