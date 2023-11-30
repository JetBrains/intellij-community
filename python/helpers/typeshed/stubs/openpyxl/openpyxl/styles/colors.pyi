from _typeshed import Incomplete, Unused
from collections.abc import Iterator
from re import Pattern
from typing import ClassVar, TypeVar, overload
from typing_extensions import Final, Literal, Self

from openpyxl.descriptors import Strict, Typed
from openpyxl.descriptors.base import (
    _N,
    Bool,
    Integer,
    MinMax,
    String,
    _ConvertibleToBool,
    _ConvertibleToFloat,
    _ConvertibleToInt,
)
from openpyxl.descriptors.serialisable import Serialisable

_S = TypeVar("_S", bound=Serialisable)

COLOR_INDEX: Final[tuple[str, ...]]
BLACK: Final = "00000000"
WHITE: Final = "00FFFFFF"
BLUE: Final = "00FFFFFF"
aRGB_REGEX: Final[Pattern[str]]

class RGB(Typed[str, _N]):
    expected_type: type[str]
    @overload
    def __init__(self: RGB[Literal[True]], name: str | None = None, *, allow_none: Literal[True]) -> None: ...
    @overload
    def __init__(self: RGB[Literal[False]], name: str | None = None, *, allow_none: Literal[False] = False) -> None: ...
    @overload
    def __set__(self: RGB[Literal[True]], instance: Serialisable | Strict, value: str | None) -> None: ...
    @overload
    def __set__(self: RGB[Literal[False]], instance: Serialisable | Strict, value: str) -> None: ...

class Color(Serialisable):
    tagname: ClassVar[str]
    rgb: RGB[Literal[False]]
    indexed: Integer[Literal[False]]
    auto: Bool[Literal[False]]
    theme: Integer[Literal[False]]
    tint: MinMax[float, Literal[False]]
    type: String[Literal[False]]
    def __init__(
        self,
        rgb="00000000",
        indexed: _ConvertibleToInt | None = None,
        auto: _ConvertibleToBool | None = None,
        theme: _ConvertibleToInt | None = None,
        tint: _ConvertibleToFloat = 0.0,
        index: _ConvertibleToInt | None = None,
        type: Unused = "rgb",
    ) -> None: ...
    @property
    def value(self) -> str | int | bool: ...
    @value.setter
    def value(self, value: str | _ConvertibleToInt | _ConvertibleToBool) -> None: ...
    def __iter__(self) -> Iterator[tuple[str, str]]: ...
    @property
    def index(self) -> str | int | bool: ...
    @overload
    def __add__(self, other: Color) -> Self: ...
    @overload
    def __add__(self, other: _S) -> _S: ...

class ColorDescriptor(Typed[Color, _N]):
    expected_type: type[Color]
    @overload
    def __init__(self: ColorDescriptor[Literal[True]], name: str | None = None, *, allow_none: Literal[True]) -> None: ...
    @overload
    def __init__(
        self: ColorDescriptor[Literal[False]], name: str | None = None, *, allow_none: Literal[False] = False
    ) -> None: ...
    @overload
    def __set__(self: ColorDescriptor[_N], instance: Serialisable | Strict, value: str) -> None: ...
    @overload
    def __set__(self: ColorDescriptor[Literal[True]], instance: Serialisable | Strict, value: Color | None) -> None: ...
    @overload
    def __set__(self: ColorDescriptor[Literal[False]], instance: Serialisable | Strict, value: Color) -> None: ...

class RgbColor(Serialisable):
    tagname: ClassVar[str]
    rgb: RGB[Literal[False]]
    def __init__(self, rgb: str) -> None: ...

class ColorList(Serialisable):
    tagname: ClassVar[str]
    indexedColors: Incomplete
    mruColors: Incomplete
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(
        self, indexedColors: list[RgbColor] | tuple[RgbColor, ...] = (), mruColors: list[Color] | tuple[Color, ...] = ()
    ) -> None: ...
    def __bool__(self) -> bool: ...
    @property
    def index(self) -> list[str]: ...
