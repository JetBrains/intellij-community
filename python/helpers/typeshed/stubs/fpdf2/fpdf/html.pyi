from _typeshed import Incomplete, SupportsItemAccess, SupportsKeysAndGetItem, Unused
from collections.abc import Callable, Iterable, Mapping
from html.parser import HTMLParser
from logging import Logger
from typing import ClassVar, Final, Literal, TypedDict, type_check_only
from typing_extensions import TypeAlias

from fpdf import FPDF

from .fonts import FontFace
from .table import Row, Table

__author__: Final[str]
__copyright__: Final[str]

_OLType: TypeAlias = Literal["1", "a", "A", "I", "i"]

LOGGER: Logger
BULLET_WIN1252: Final[str]
DEGREE_WIN1252: Final[str]
HEADING_TAGS: Final[tuple[str, ...]]
DEFAULT_TAG_STYLES: Final[dict[str, FontFace]]
DEFAULT_TAG_INDENTS: Final[dict[str, int]]

COLOR_DICT: Final[dict[str, str]]

def color_as_decimal(color: str | None = "#000000") -> tuple[int, int, int] | None: ...
def parse_style(elem_attrs: SupportsItemAccess[str, str]) -> None: ...
@type_check_only
class _Emphasis(TypedDict):
    b: bool
    i: bool
    u: bool

class HTML2FPDF(HTMLParser):
    HTML_UNCLOSED_TAGS: ClassVar[tuple[str, ...]]
    TABLE_LINE_HEIGHT: ClassVar[float]

    pdf: FPDF
    image_map: Callable[[str], str]
    ul_bullet_char: str
    li_prefix_color: tuple[int, int, int]
    warn_on_tags_not_matching: bool
    emphasis: _Emphasis
    font_size: float
    follows_trailing_space: bool
    follows_heading: bool
    href: str
    align: str
    style_stack: list[FontFace]
    indent: int
    ol_type: list[_OLType]
    bullet: list[Incomplete]
    font_color: tuple[int, int, int]
    heading_level: Incomplete | None
    heading_above: float
    heading_below: float
    table_line_separators: bool
    table: Table | None
    table_row: Row | None
    tr: dict[str, str] | None
    td_th: dict[str, str] | None
    tag_indents: dict[str, int]
    tag_styles: dict[str, FontFace]

    # Not initialized in __init__:
    font_family: str
    h: float

    def __init__(
        self,
        pdf: FPDF,
        image_map: Callable[[str], str] | None = None,
        li_tag_indent: int = 5,
        dd_tag_indent: int = 10,
        table_line_separators: bool = False,
        ul_bullet_char: str = "\x95",
        li_prefix_color: tuple[int, int, int] = (190, 0, 0),
        heading_sizes: SupportsKeysAndGetItem[str, int] | Iterable[tuple[str, int]] | None = None,
        pre_code_font: str = ...,
        warn_on_tags_not_matching: bool = True,
        tag_indents: dict[str, int] | None = None,
        tag_styles: Mapping[str, FontFace] | None = None,
        **_: Unused,
    ): ...
    def handle_data(self, data) -> None: ...
    def handle_starttag(self, tag, attrs) -> None: ...
    def handle_endtag(self, tag) -> None: ...
    def set_font(self, family: str | None = None, size: float | None = None, set_default: bool = False) -> None: ...
    def set_style(self, tag: Incomplete | None = None, enable: bool = False) -> None: ...
    def set_text_color(self, r: Incomplete | None = None, g: int = 0, b: int = 0) -> None: ...
    def put_link(self, text) -> None: ...
    def render_toc(self, pdf, outline) -> None: ...
    def error(self, message: str) -> None: ...

def ul_prefix(ul_type: str) -> str: ...
def ol_prefix(ol_type: _OLType, index: int) -> str: ...

class HTMLMixin:
    def __init__(self, *args, **kwargs) -> None: ...
