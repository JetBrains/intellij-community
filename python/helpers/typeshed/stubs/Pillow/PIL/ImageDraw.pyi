from collections.abc import Container, Sequence
from typing import Any, overload
from typing_extensions import Literal, TypeAlias

from .Image import Image
from .ImageColor import _Ink
from .ImageFont import _Font

_XY: TypeAlias = Sequence[float | tuple[float, float]]
_Outline: TypeAlias = Any

class ImageDraw:
    def __init__(self, im: Image, mode: str | None = ...) -> None: ...
    def getfont(self): ...
    def arc(self, xy: _XY, start: float, end: float, fill: _Ink | None = ..., width: float = ...) -> None: ...
    def bitmap(self, xy: _XY, bitmap: Image, fill: _Ink | None = ...) -> None: ...
    def chord(
        self, xy: _XY, start: float, end: float, fill: _Ink | None = ..., outline: _Ink | None = ..., width: float = ...
    ) -> None: ...
    def ellipse(self, xy: _XY, fill: _Ink | None = ..., outline: _Ink | None = ..., width: float = ...) -> None: ...
    def line(self, xy: _XY, fill: _Ink | None = ..., width: float = ..., joint: Literal["curve"] | None = ...) -> None: ...
    def shape(self, shape: _Outline, fill: _Ink | None = ..., outline: _Ink | None = ...) -> None: ...
    def pieslice(
        self,
        xy: tuple[tuple[float, float], tuple[float, float]],
        start: float,
        end: float,
        fill: _Ink | None = ...,
        outline: _Ink | None = ...,
        width: float = ...,
    ) -> None: ...
    def point(self, xy: _XY, fill: _Ink | None = ...) -> None: ...
    def polygon(self, xy: _XY, fill: _Ink | None = ..., outline: _Ink | None = ..., width: float = ...) -> None: ...
    def regular_polygon(
        self,
        bounding_circle: tuple[float, float] | tuple[float, float, float] | list[int],
        n_sides: int,
        rotation: float = ...,
        fill: _Ink | None = ...,
        outline: _Ink | None = ...,
    ) -> None: ...
    def rectangle(
        self,
        xy: tuple[float, float, float, float] | tuple[tuple[float, float], tuple[float, float]],
        fill: _Ink | None = ...,
        outline: _Ink | None = ...,
        width: float = ...,
    ) -> None: ...
    def rounded_rectangle(
        self,
        xy: tuple[float, float, float, float] | tuple[tuple[float, float], tuple[float, float]],
        radius: float = ...,
        fill: _Ink | None = ...,
        outline: _Ink | None = ...,
        width: float = ...,
    ) -> None: ...
    def text(
        self,
        xy: tuple[float, float],
        text: str | bytes,
        fill: _Ink | None = ...,
        font: _Font | None = ...,
        anchor: str | None = ...,
        spacing: float = ...,
        align: Literal["left", "center", "right"] = ...,
        direction: Literal["rtl", "ltr", "ttb"] | None = ...,
        features: Sequence[str] | None = ...,
        language: str | None = ...,
        stroke_width: int = ...,
        stroke_fill: _Ink | None = ...,
        embedded_color: bool = ...,
        *args,
        **kwargs,
    ) -> None: ...
    def multiline_text(
        self,
        xy: tuple[float, float],
        text: str | bytes,
        fill: _Ink | None = ...,
        font: _Font | None = ...,
        anchor: str | None = ...,
        spacing: float = ...,
        align: Literal["left", "center", "right"] = ...,
        direction: Literal["rtl", "ltr", "ttb"] | None = ...,
        features: Any | None = ...,
        language: str | None = ...,
        stroke_width: int = ...,
        stroke_fill: _Ink | None = ...,
        embedded_color: bool = ...,
    ) -> None: ...
    def textsize(
        self,
        text: str | bytes,
        font: _Font | None = ...,
        spacing: float = ...,
        direction: Literal["rtl", "ltr", "ttb"] | None = ...,
        features: Sequence[str] | None = ...,
        language: str | None = ...,
        stroke_width: int = ...,
    ) -> tuple[int, int]: ...
    def multiline_textsize(
        self,
        text: str | bytes,
        font: _Font | None = ...,
        spacing: float = ...,
        direction: Literal["rtl", "ltr", "ttb"] | None = ...,
        features: Sequence[str] | None = ...,
        language: str | None = ...,
        stroke_width: int = ...,
    ) -> tuple[int, int]: ...
    def textlength(
        self,
        text: str | bytes,
        font: _Font | None = ...,
        direction: Literal["rtl", "ltr", "ttb"] | None = ...,
        features: Sequence[str] | None = ...,
        language: str | None = ...,
        embedded_color: bool = ...,
    ) -> int: ...
    def textbbox(
        self,
        xy: tuple[float, float],
        text: str | bytes,
        font: _Font | None = ...,
        anchor: str | None = ...,
        spacing: float = ...,
        align: Literal["left", "center", "right"] = ...,
        direction: Literal["rtl", "ltr", "ttb"] | None = ...,
        features: Any | None = ...,
        language: str | None = ...,
        stroke_width: int = ...,
        embedded_color: bool = ...,
    ) -> tuple[int, int, int, int]: ...
    def multiline_textbbox(
        self,
        xy: tuple[float, float],
        text: str | bytes,
        font: _Font | None = ...,
        anchor: str | None = ...,
        spacing: float = ...,
        align: Literal["left", "center", "right"] = ...,
        direction: Literal["rtl", "ltr", "ttb"] | None = ...,
        features: Any | None = ...,
        language: str | None = ...,
        stroke_width: int = ...,
        embedded_color: bool = ...,
    ) -> tuple[int, int, int, int]: ...

def Draw(im: Image, mode: str | None = ...) -> ImageDraw: ...
def Outline() -> _Outline: ...
@overload
def getdraw(im: None = ..., hints: Container[Literal["nicest"]] | None = ...) -> tuple[None, Any]: ...
@overload
def getdraw(im: Image, hints: Container[Literal["nicest"]] | None = ...) -> tuple[Image, Any]: ...
def floodfill(image: Image, xy: tuple[float, float], value, border=..., thresh: float = ...) -> None: ...
