from typing import Any

from openpyxl.descriptors import Sequence
from openpyxl.descriptors.serialisable import Serialisable

FILL_NONE: str
FILL_SOLID: str
FILL_PATTERN_DARKDOWN: str
FILL_PATTERN_DARKGRAY: str
FILL_PATTERN_DARKGRID: str
FILL_PATTERN_DARKHORIZONTAL: str
FILL_PATTERN_DARKTRELLIS: str
FILL_PATTERN_DARKUP: str
FILL_PATTERN_DARKVERTICAL: str
FILL_PATTERN_GRAY0625: str
FILL_PATTERN_GRAY125: str
FILL_PATTERN_LIGHTDOWN: str
FILL_PATTERN_LIGHTGRAY: str
FILL_PATTERN_LIGHTGRID: str
FILL_PATTERN_LIGHTHORIZONTAL: str
FILL_PATTERN_LIGHTTRELLIS: str
FILL_PATTERN_LIGHTUP: str
FILL_PATTERN_LIGHTVERTICAL: str
FILL_PATTERN_MEDIUMGRAY: str
fills: Any

class Fill(Serialisable):
    tagname: str
    @classmethod
    def from_tree(cls, el): ...

class PatternFill(Fill):
    tagname: str
    __elements__: Any
    patternType: Any
    fill_type: Any
    fgColor: Any
    start_color: Any
    bgColor: Any
    end_color: Any
    def __init__(
        self,
        patternType: Any | None = ...,
        fgColor=...,
        bgColor=...,
        fill_type: Any | None = ...,
        start_color: Any | None = ...,
        end_color: Any | None = ...,
    ) -> None: ...
    def to_tree(self, tagname: Any | None = ..., idx: Any | None = ...): ...  # type: ignore[override]

DEFAULT_EMPTY_FILL: Any
DEFAULT_GRAY_FILL: Any

class Stop(Serialisable):
    tagname: str
    position: Any
    color: Any
    def __init__(self, color, position) -> None: ...

class StopList(Sequence):
    expected_type: Any
    def __set__(self, obj, values) -> None: ...

class GradientFill(Fill):
    tagname: str
    type: Any
    fill_type: Any
    degree: Any
    left: Any
    right: Any
    top: Any
    bottom: Any
    stop: Any
    def __init__(
        self, type: str = ..., degree: int = ..., left: int = ..., right: int = ..., top: int = ..., bottom: int = ..., stop=...
    ) -> None: ...
    def __iter__(self): ...
    def to_tree(self, tagname: Any | None = ..., namespace: Any | None = ..., idx: Any | None = ...): ...  # type: ignore[override]
