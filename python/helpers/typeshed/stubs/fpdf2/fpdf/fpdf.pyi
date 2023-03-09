import datetime
from _typeshed import Incomplete, StrPath
from collections import defaultdict
from collections.abc import Callable, Sequence
from contextlib import _GeneratorContextManager
from enum import IntEnum
from io import BytesIO
from typing import Any, ClassVar, NamedTuple, overload
from typing_extensions import Literal, TypeAlias

from PIL import Image

from .actions import Action
from .enums import Align, AnnotationFlag, AnnotationName, Corner, PageLayout, RenderStyle, TextMarkupType, XPos, YPos
from .recorder import FPDFRecorder
from .syntax import DestinationXYZ
from .util import _Unit

_Orientation: TypeAlias = Literal["", "portrait", "p", "P", "landscape", "l", "L"]
_Format: TypeAlias = Literal["", "a3", "A3", "a4", "A4", "a5", "A5", "letter", "Letter", "legal", "Legal"]
_FontStyle: TypeAlias = Literal["", "B", "I"]
_FontStyles: TypeAlias = Literal["", "B", "I", "U", "BU", "UB", "BI", "IB", "IU", "UI", "BIU", "BUI", "IBU", "IUB", "UBI", "UIB"]
PAGE_FORMATS: dict[_Format, tuple[float, float]]

class DocumentState(IntEnum):
    UNINITIALIZED: int
    READY: int
    GENERATING_PAGE: int
    CLOSED: int

class Annotation(NamedTuple):
    type: str
    x: int
    y: int
    width: int
    height: int
    flags: tuple[AnnotationFlag, ...] = ...
    contents: str | None = ...
    link: str | int | None = ...
    alt_text: str | None = ...
    action: Action | None = ...
    color: int | None = ...
    modification_time: datetime.datetime | None = ...
    title: str | None = ...
    quad_points: Sequence[int] | None = ...
    page: int | None = ...
    border_width: int = ...
    name: AnnotationName | None = ...
    ink_list: tuple[int, ...] = ...

class TitleStyle(NamedTuple):
    font_family: str | None = ...
    font_style: str | None = ...
    font_size_pt: int | None = ...
    color: int | tuple[int, int, int] | None = ...
    underline: bool = ...
    t_margin: int | None = ...
    l_margin: int | None = ...
    b_margin: int | None = ...

class ToCPlaceholder(NamedTuple):
    render_function: Callable[[FPDF, Any], object]
    start_page: int
    y: int
    pages: int = ...

class SubsetMap:
    def __init__(self, identities: list[int]) -> None: ...
    def pick(self, unicode: int) -> int: ...
    def dict(self) -> dict[int, int]: ...

def get_page_format(format: _Format | tuple[float, float], k: float | None = ...) -> tuple[float, float]: ...

# TODO: TypedDicts
_Page: TypeAlias = dict[str, Any]
_Font: TypeAlias = dict[str, Any]
_FontFile: TypeAlias = dict[str, Any]
_Image: TypeAlias = dict[str, Any]

class FPDF:
    MARKDOWN_BOLD_MARKER: ClassVar[str]
    MARKDOWN_ITALICS_MARKER: ClassVar[str]
    MARKDOWN_UNDERLINE_MARKER: ClassVar[str]
    offsets: dict[int, int]
    page: int
    n: int
    buffer: bytearray
    pages: dict[int, _Page]
    state: DocumentState
    fonts: dict[str, _Font]
    font_files: dict[str, _FontFile]
    diffs: dict[int, int]
    images: dict[str, _Image]
    annots: defaultdict[int, list[Annotation]]
    links: dict[int, DestinationXYZ]
    in_footer: int
    lasth: int
    current_font: _Font
    font_family: str
    font_style: str
    str_alias_nb_pages: str
    underline: int
    draw_color: str
    fill_color: str
    text_color: str
    ws: int
    angle: int
    xmp_metadata: str | None
    image_filter: str
    page_duration: int
    page_transition: Incomplete | None
    struct_builder: Incomplete
    section_title_styles: Incomplete
    core_fonts: Incomplete
    core_fonts_encoding: str
    font_aliases: Incomplete
    k: float
    def_orientation: Incomplete
    font_size: float
    c_margin: float
    line_width: float
    dw_pt: float
    dh_pt: float
    pdf_version: str

    x: float
    y: float

    # Set during call to _set_orientation(), called from __init__().
    cur_orientation: Literal["P", "L"]
    w_pt: float
    h_pt: float
    w: float
    h: float
    def __init__(
        self,
        orientation: _Orientation = ...,
        unit: _Unit | float = ...,
        format: _Format | tuple[float, float] = ...,
        font_cache_dir: str | Literal["DEPRECATED"] = ...,
    ) -> None: ...
    @property
    def font_size_pt(self) -> float: ...
    @property
    def unifontsubset(self) -> bool: ...
    @property
    def epw(self) -> float: ...
    @property
    def eph(self) -> float: ...
    def set_margin(self, margin: float) -> None: ...
    def set_margins(self, left: float, top: float, right: float = ...) -> None: ...
    l_margin: float
    def set_left_margin(self, margin: float) -> None: ...
    t_margin: float
    def set_top_margin(self, margin: float) -> None: ...
    r_margin: float
    def set_right_margin(self, margin: float) -> None: ...
    auto_page_break: bool
    b_margin: float
    page_break_trigger: float
    def set_auto_page_break(self, auto: bool, margin: float = ...) -> None: ...
    zoom_mode: Literal["fullpage", "fullwidth", "real", "default"] | float
    page_layout: PageLayout | None
    def set_display_mode(
        self,
        zoom: Literal["fullpage", "fullwidth", "real", "default"] | float,
        layout: Literal["single", "continuous", "two", "default"] = ...,
    ) -> None: ...
    compress: bool
    def set_compression(self, compress: bool) -> None: ...
    title: str
    def set_title(self, title: str) -> None: ...
    lang: str
    def set_lang(self, lang: str) -> None: ...
    subject: str
    def set_subject(self, subject: str) -> None: ...
    author: str
    def set_author(self, author: str) -> None: ...
    keywords: str
    def set_keywords(self, keywords: str) -> None: ...
    creator: str
    def set_creator(self, creator: str) -> None: ...
    producer: str
    def set_producer(self, producer: str) -> None: ...
    creation_date: datetime.datetime | bool | None
    def set_creation_date(self, date: datetime.datetime | bool | None = ...) -> None: ...
    def set_xmp_metadata(self, xmp_metadata: str) -> None: ...
    def set_doc_option(self, opt: str, value: str) -> None: ...
    def set_image_filter(self, image_filter: str) -> None: ...
    def alias_nb_pages(self, alias: str = ...) -> None: ...
    def open(self) -> None: ...
    def close(self) -> None: ...
    def add_page(
        self,
        orientation: _Orientation = ...,
        format: _Format | tuple[float, float] = ...,
        same: bool = ...,
        duration: int = ...,
        transition: Incomplete | None = ...,
    ) -> None: ...
    def header(self) -> None: ...
    def footer(self) -> None: ...
    def page_no(self) -> int: ...
    def set_draw_color(self, r: int, g: int = ..., b: int = ...) -> None: ...
    def set_fill_color(self, r: int, g: int = ..., b: int = ...) -> None: ...
    def set_text_color(self, r: int, g: int = ..., b: int = ...) -> None: ...
    def get_string_width(self, s: str, normalized: bool = ..., markdown: bool = ...) -> float: ...
    def set_line_width(self, width: float) -> None: ...
    def line(self, x1: float, y1: float, x2: float, y2: float) -> None: ...
    def polyline(
        self, point_list: list[tuple[float, float]], fill: bool = ..., polygon: bool = ..., style: RenderStyle | str | None = ...
    ) -> None: ...
    def polygon(self, point_list: list[tuple[float, float]], fill: bool = ..., style: RenderStyle | str | None = ...) -> None: ...
    def dashed_line(self, x1, y1, x2, y2, dash_length: int = ..., space_length: int = ...) -> None: ...
    def rect(
        self,
        x: float,
        y: float,
        w: float,
        h: float,
        style: RenderStyle | str | None = ...,
        round_corners: tuple[str, ...] | tuple[Corner, ...] | bool = ...,
        corner_radius: float = ...,
    ) -> None: ...
    def ellipse(self, x: float, y: float, w: float, h: float, style: RenderStyle | str | None = ...) -> None: ...
    def circle(self, x: float, y: float, r, style: RenderStyle | str | None = ...) -> None: ...
    def regular_polygon(
        self,
        x: float,
        y: float,
        numSides: int,
        polyWidth: float,
        rotateDegrees: float = ...,
        style: RenderStyle | str | None = ...,
    ): ...
    def star(
        self,
        x: float,
        y: float,
        r_in: float,
        r_out: float,
        corners: int,
        rotate_degrees: float = ...,
        style: RenderStyle | str | None = ...,
    ): ...
    def add_font(
        self, family: str, style: _FontStyle = ..., fname: str | None = ..., uni: bool | Literal["DEPRECATED"] = ...
    ) -> None: ...
    def set_font(self, family: str | None = ..., style: _FontStyles = ..., size: int = ...) -> None: ...
    def set_font_size(self, size: float) -> None: ...
    font_stretching: float
    def set_stretching(self, stretching: float) -> None: ...
    def add_link(self) -> int: ...
    def set_link(self, link, y: int = ..., x: int = ..., page: int = ..., zoom: float | Literal["null"] = ...) -> None: ...
    def link(
        self, x: float, y: float, w: float, h: float, link: str | int, alt_text: str | None = ..., border_width: int = ...
    ) -> Annotation: ...
    def text_annotation(
        self,
        x: float,
        y: float,
        text: str,
        w: float = ...,
        h: float = ...,
        name: AnnotationName | str | None = ...,
        flags: tuple[AnnotationFlag, ...] | tuple[str, ...] = ...,
    ) -> None: ...
    def add_action(self, action, x: float, y: float, w: float, h: float) -> None: ...
    def highlight(
        self,
        text: str,
        title: str = ...,
        type: TextMarkupType | str = ...,
        color: tuple[float, float, float] = ...,
        modification_time: datetime.datetime | None = ...,
    ) -> _GeneratorContextManager[None]: ...
    add_highlight = highlight
    def add_text_markup_annotation(
        self,
        type: str,
        text: str,
        quad_points: Sequence[int],
        title: str = ...,
        color: tuple[float, float, float] = ...,
        modification_time: datetime.datetime | None = ...,
        page: int | None = ...,
    ) -> Annotation: ...
    def text(self, x: float, y: float, txt: str = ...) -> None: ...
    def rotate(self, angle: float, x: float | None = ..., y: float | None = ...) -> None: ...
    def rotation(self, angle: float, x: float | None = ..., y: float | None = ...) -> _GeneratorContextManager[None]: ...
    @property
    def accept_page_break(self) -> bool: ...
    def cell(
        self,
        w: float | None = ...,
        h: float | None = ...,
        txt: str = ...,
        border: bool | Literal[0, 1] | str = ...,
        ln: int | Literal["DEPRECATED"] = ...,
        align: str | Align = ...,
        fill: bool = ...,
        link: str = ...,
        center: bool | Literal["DEPRECATED"] = ...,
        markdown: bool = ...,
        new_x: XPos | str = ...,
        new_y: YPos | str = ...,
    ) -> bool: ...
    def will_page_break(self, height: float) -> bool: ...
    def multi_cell(
        self,
        w: float,
        h: float | None = ...,
        txt: str = ...,
        border: bool | Literal[0, 1] | str = ...,
        align: str | Align = ...,
        fill: bool = ...,
        split_only: bool = ...,
        link: str | int = ...,
        ln: int | Literal["DEPRECATED"] = ...,
        max_line_height: float | None = ...,
        markdown: bool = ...,
        print_sh: bool = ...,
        new_x: XPos | str = ...,
        new_y: YPos | str = ...,
    ): ...
    def write(self, h: float | None = ..., txt: str = ..., link: str = ..., print_sh: bool = ...) -> None: ...
    def image(
        self,
        name: str | Image.Image | BytesIO | StrPath,
        x: float | None = ...,
        y: float | None = ...,
        w: float = ...,
        h: float = ...,
        type: str = ...,
        link: str = ...,
        title: str | None = ...,
        alt_text: str | None = ...,
    ) -> _Image: ...
    def ln(self, h: float | None = ...) -> None: ...
    def get_x(self) -> float: ...
    def set_x(self, x: float) -> None: ...
    def get_y(self) -> float: ...
    def set_y(self, y: float) -> None: ...
    def set_xy(self, x: float, y: float) -> None: ...
    @overload
    def output(self, name: Literal[""] = ...) -> bytearray: ...  # type: ignore[misc]
    @overload
    def output(self, name: str) -> None: ...
    def normalize_text(self, txt: str) -> str: ...
    def interleaved2of5(self, txt, x: float, y: float, w: float = ..., h: float = ...) -> None: ...
    def code39(self, txt, x: float, y: float, w: float = ..., h: float = ...) -> None: ...
    def rect_clip(self, x: float, y: float, w: float, h: float) -> _GeneratorContextManager[None]: ...
    def unbreakable(self) -> _GeneratorContextManager[FPDFRecorder]: ...
    def insert_toc_placeholder(self, render_toc_function, pages: int = ...) -> None: ...
    def set_section_title_styles(
        self,
        level0: TitleStyle,
        level1: TitleStyle | None = ...,
        level2: TitleStyle | None = ...,
        level3: TitleStyle | None = ...,
        level4: TitleStyle | None = ...,
        level5: TitleStyle | None = ...,
        level6: TitleStyle | None = ...,
    ) -> None: ...
    def start_section(self, name: str, level: int = ...) -> None: ...
