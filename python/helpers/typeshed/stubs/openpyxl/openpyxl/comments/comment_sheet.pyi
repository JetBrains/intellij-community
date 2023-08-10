from collections.abc import Generator
from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Properties(Serialisable):
    locked: Any
    defaultSize: Any
    disabled: Any
    uiObject: Any
    autoFill: Any
    autoLine: Any
    altText: Any
    textHAlign: Any
    textVAlign: Any
    lockText: Any
    justLastX: Any
    autoScale: Any
    rowHidden: Any
    colHidden: Any
    __elements__: Any
    anchor: Any
    def __init__(
        self,
        locked: Any | None = ...,
        defaultSize: Any | None = ...,
        _print: Any | None = ...,
        disabled: Any | None = ...,
        uiObject: Any | None = ...,
        autoFill: Any | None = ...,
        autoLine: Any | None = ...,
        altText: Any | None = ...,
        textHAlign: Any | None = ...,
        textVAlign: Any | None = ...,
        lockText: Any | None = ...,
        justLastX: Any | None = ...,
        autoScale: Any | None = ...,
        rowHidden: Any | None = ...,
        colHidden: Any | None = ...,
        anchor: Any | None = ...,
    ) -> None: ...

class CommentRecord(Serialisable):
    tagname: str
    ref: Any
    authorId: Any
    guid: Any
    shapeId: Any
    text: Any
    commentPr: Any
    author: Any
    __elements__: Any
    __attrs__: Any
    height: Any
    width: Any
    def __init__(
        self,
        ref: str = ...,
        authorId: int = ...,
        guid: Any | None = ...,
        shapeId: int = ...,
        text: Any | None = ...,
        commentPr: Any | None = ...,
        author: Any | None = ...,
        height: int = ...,
        width: int = ...,
    ) -> None: ...
    @classmethod
    def from_cell(cls, cell): ...
    @property
    def content(self): ...

class CommentSheet(Serialisable):
    tagname: str
    authors: Any
    commentList: Any
    extLst: Any
    mime_type: str
    __elements__: Any
    def __init__(self, authors: Any | None = ..., commentList: Any | None = ..., extLst: Any | None = ...) -> None: ...
    def to_tree(self): ...
    @property
    def comments(self) -> Generator[Any, None, None]: ...
    @classmethod
    def from_comments(cls, comments): ...
    def write_shapes(self, vml: Any | None = ...): ...
    @property
    def path(self): ...
