from _typeshed import Incomplete
from collections.abc import Iterator
from typing import ClassVar
from typing_extensions import Final, Literal, TypeAlias

from openpyxl.descriptors.base import Alias, Bool, Min, MinMax, NoneSet, _ConvertibleToBool, _ConvertibleToFloat
from openpyxl.descriptors.serialisable import Serialisable

_HorizontalAlignmentsType: TypeAlias = Literal[
    "general", "left", "center", "right", "fill", "justify", "centerContinuous", "distributed"
]
_VerticalAlignmentsType: TypeAlias = Literal["top", "center", "bottom", "justify", "distributed"]

horizontal_alignments: Final[tuple[_HorizontalAlignmentsType, ...]]
vertical_aligments: Final[tuple[_VerticalAlignmentsType, ...]]

class Alignment(Serialisable):
    tagname: ClassVar[str]
    __fields__: ClassVar[tuple[str, ...]]
    horizontal: NoneSet[_HorizontalAlignmentsType]
    vertical: NoneSet[_VerticalAlignmentsType]
    textRotation: NoneSet[int]
    text_rotation: Alias
    wrapText: Bool[Literal[True]]
    wrap_text: Alias
    shrinkToFit: Bool[Literal[True]]
    shrink_to_fit: Alias
    indent: MinMax[float, Literal[False]]
    relativeIndent: MinMax[float, Literal[False]]
    justifyLastLine: Bool[Literal[True]]
    readingOrder: Min[float, Literal[False]]
    def __init__(
        self,
        horizontal: Incomplete | None = None,
        vertical: Incomplete | None = None,
        textRotation: int = 0,
        wrapText: _ConvertibleToBool | None = None,
        shrinkToFit: _ConvertibleToBool | None = None,
        indent: _ConvertibleToFloat = 0,
        relativeIndent: _ConvertibleToFloat = 0,
        justifyLastLine: _ConvertibleToBool | None = None,
        readingOrder: _ConvertibleToFloat = 0,
        text_rotation: Incomplete | None = None,
        wrap_text: Incomplete | None = None,
        shrink_to_fit: Incomplete | None = None,
        mergeCell: Incomplete | None = None,
    ) -> None: ...
    def __iter__(self) -> Iterator[tuple[str, str]]: ...
