from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

horizontal_alignments: Any
vertical_aligments: Any

class Alignment(Serialisable):
    tagname: str
    __fields__: Any
    horizontal: Any
    vertical: Any
    textRotation: Any
    text_rotation: Any
    wrapText: Any
    wrap_text: Any
    shrinkToFit: Any
    shrink_to_fit: Any
    indent: Any
    relativeIndent: Any
    justifyLastLine: Any
    readingOrder: Any
    def __init__(
        self,
        horizontal: Any | None = ...,
        vertical: Any | None = ...,
        textRotation: int = ...,
        wrapText: Any | None = ...,
        shrinkToFit: Any | None = ...,
        indent: int = ...,
        relativeIndent: int = ...,
        justifyLastLine: Any | None = ...,
        readingOrder: int = ...,
        text_rotation: Any | None = ...,
        wrap_text: Any | None = ...,
        shrink_to_fit: Any | None = ...,
        mergeCell: Any | None = ...,
    ) -> None: ...
    def __iter__(self): ...
