from _typeshed import StrOrBytesPath, SupportsRead
from typing import Any, Protocol
from typing_extensions import Literal

LAYOUT_BASIC: Literal[0]
LAYOUT_RAQM: Literal[1]

class _Font(Protocol):
    def getmask(self, text: str | bytes, mode: str = ..., direction=..., features=...): ...

class ImageFont:
    def getsize(self, text: str | bytes, *args, **kwargs) -> tuple[int, int]: ...
    def getmask(self, text: str | bytes, mode: str = ..., direction=..., features=...): ...

class FreeTypeFont:
    path: str | bytes | SupportsRead[bytes] | None
    size: int
    index: int
    encoding: str
    layout_engine: Any
    def __init__(
        self,
        font: str | bytes | SupportsRead[bytes] | None = ...,
        size: int = ...,
        index: int = ...,
        encoding: str = ...,
        layout_engine: int | None = ...,
    ) -> None: ...
    def getname(self) -> tuple[str, str]: ...
    def getmetrics(self) -> tuple[int, int]: ...
    def getlength(
        self,
        text: str | bytes,
        mode: str = ...,
        direction: Literal["ltr", "rtl", "ttb"] | None = ...,
        features: Any | None = ...,
        language: str | None = ...,
    ) -> int: ...
    def getbbox(
        self,
        text: str | bytes,
        mode: str = ...,
        direction=...,
        features=...,
        language: str | None = ...,
        stroke_width: int = ...,
        anchor: str | None = ...,
    ) -> tuple[int, int, int, int]: ...
    def getsize(
        self,
        text: str | bytes,
        direction: Literal["ltr", "rtl", "ttb"] | None = ...,
        features: Any | None = ...,
        language: str | None = ...,
        stroke_width: int = ...,
    ) -> tuple[int, int]: ...
    def getsize_multiline(
        self,
        text: str | bytes,
        direction: Literal["ltr", "rtl", "ttb"] | None = ...,
        spacing: float = ...,
        features: Any | None = ...,
        language: str | None = ...,
        stroke_width: float = ...,
    ) -> tuple[int, int]: ...
    def getoffset(self, text: str | bytes) -> tuple[int, int]: ...
    def getmask(
        self,
        text: str | bytes,
        mode: str = ...,
        direction: Literal["ltr", "rtl", "ttb"] | None = ...,
        features: Any | None = ...,
        language: str | None = ...,
        stroke_width: float = ...,
        anchor: str | None = ...,
        ink=...,
    ): ...
    def getmask2(
        self,
        text: str | bytes,
        mode: str = ...,
        fill=...,
        direction: Literal["ltr", "rtl", "ttb"] | None = ...,
        features: Any | None = ...,
        language: str | None = ...,
        stroke_width: float = ...,
        anchor: str | None = ...,
        ink=...,
        *args,
        **kwargs,
    ): ...
    def font_variant(
        self,
        font: str | bytes | SupportsRead[bytes] | None = ...,
        size: int | None = ...,
        index: int | None = ...,
        encoding: str | None = ...,
        layout_engine: int | None = ...,
    ) -> FreeTypeFont: ...
    def get_variation_names(self): ...
    def set_variation_by_name(self, name): ...
    def get_variation_axes(self): ...
    def set_variation_by_axes(self, axes): ...

class TransposedFont:
    def __init__(self, font: _Font, orientation: int | None = ...) -> None: ...
    def getsize(self, text: str | bytes, *args, **kwargs) -> tuple[int, int]: ...
    def getmask(self, text: str | bytes, mode: str = ..., *args, **kwargs): ...

def load(filename: StrOrBytesPath | int) -> ImageFont: ...
def truetype(
    font: str | bytes | SupportsRead[bytes] | None = ...,
    size: int = ...,
    index: int = ...,
    encoding: str = ...,
    layout_engine: int | None = ...,
) -> FreeTypeFont: ...
def load_path(filename: str | bytes) -> ImageFont: ...
def load_default() -> ImageFont: ...
