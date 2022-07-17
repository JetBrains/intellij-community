from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class RichText(Serialisable):
    tagname: str
    bodyPr: Any
    properties: Any
    lstStyle: Any
    p: Any
    paragraphs: Any
    __elements__: Any
    def __init__(self, bodyPr: Any | None = ..., lstStyle: Any | None = ..., p: Any | None = ...) -> None: ...

class Text(Serialisable):
    tagname: str
    strRef: Any
    rich: Any
    __elements__: Any
    def __init__(self, strRef: Any | None = ..., rich: Any | None = ...) -> None: ...
    def to_tree(self, tagname: Any | None = ..., idx: Any | None = ..., namespace: Any | None = ...): ...
