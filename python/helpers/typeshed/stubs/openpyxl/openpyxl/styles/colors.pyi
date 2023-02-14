from typing import Any

from openpyxl.descriptors import Typed
from openpyxl.descriptors.serialisable import Serialisable

COLOR_INDEX: Any
BLACK: Any
WHITE: Any
BLUE: Any
aRGB_REGEX: Any

class RGB(Typed):
    expected_type: Any
    def __set__(self, instance, value) -> None: ...

class Color(Serialisable):
    tagname: str
    rgb: Any
    indexed: Any
    auto: Any
    theme: Any
    tint: Any
    type: Any
    def __init__(
        self,
        rgb=...,
        indexed: Any | None = ...,
        auto: Any | None = ...,
        theme: Any | None = ...,
        tint: float = ...,
        index: Any | None = ...,
        type: str = ...,
    ) -> None: ...
    @property
    def value(self): ...
    @value.setter
    def value(self, value) -> None: ...
    def __iter__(self): ...
    @property
    def index(self): ...
    def __add__(self, other): ...

class ColorDescriptor(Typed):
    expected_type: Any
    def __set__(self, instance, value) -> None: ...

class RgbColor(Serialisable):
    tagname: str
    rgb: Any
    def __init__(self, rgb: Any | None = ...) -> None: ...

class ColorList(Serialisable):
    tagname: str
    indexedColors: Any
    mruColors: Any
    __elements__: Any
    def __init__(self, indexedColors=..., mruColors=...) -> None: ...
    def __bool__(self): ...
    @property
    def index(self): ...
