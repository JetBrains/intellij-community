from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

BORDER_NONE: Any
BORDER_DASHDOT: str
BORDER_DASHDOTDOT: str
BORDER_DASHED: str
BORDER_DOTTED: str
BORDER_DOUBLE: str
BORDER_HAIR: str
BORDER_MEDIUM: str
BORDER_MEDIUMDASHDOT: str
BORDER_MEDIUMDASHDOTDOT: str
BORDER_MEDIUMDASHED: str
BORDER_SLANTDASHDOT: str
BORDER_THICK: str
BORDER_THIN: str

class Side(Serialisable):  # type: ignore[misc]
    __fields__: Any
    color: Any
    style: Any
    border_style: Any
    def __init__(self, style: Any | None = ..., color: Any | None = ..., border_style: Any | None = ...) -> None: ...

class Border(Serialisable):
    tagname: str
    __fields__: Any
    __elements__: Any
    start: Any
    end: Any
    left: Any
    right: Any
    top: Any
    bottom: Any
    diagonal: Any
    vertical: Any
    horizontal: Any
    outline: Any
    diagonalUp: Any
    diagonalDown: Any
    diagonal_direction: Any
    def __init__(
        self,
        left: Any | None = ...,
        right: Any | None = ...,
        top: Any | None = ...,
        bottom: Any | None = ...,
        diagonal: Any | None = ...,
        diagonal_direction: Any | None = ...,
        vertical: Any | None = ...,
        horizontal: Any | None = ...,
        diagonalUp: bool = ...,
        diagonalDown: bool = ...,
        outline: bool = ...,
        start: Any | None = ...,
        end: Any | None = ...,
    ) -> None: ...
    def __iter__(self): ...

DEFAULT_BORDER: Any
