from _typeshed import Incomplete, SupportsKeysAndGetItem, Unused
from collections.abc import Callable, Iterable
from html.parser import HTMLParser
from logging import Logger
from re import Match, Pattern
from typing import ClassVar
from typing_extensions import Final

from fpdf import FPDF

__author__: Final[str]
__copyright__: Final[str]

LOGGER: Logger
BULLET_WIN1252: Final[str]
DEFAULT_HEADING_SIZES: dict[str, int]
LEADING_SPACE: Pattern[str]
WHITESPACE: Pattern[str]
TRAILING_SPACE: Pattern[str]

COLOR_DICT: Final[dict[str, str]]

def px2mm(px: float) -> float: ...
def color_as_decimal(color: str | None = "#000000") -> tuple[int, int, int] | None: ...

class HTML2FPDF(HTMLParser):
    HTML_UNCLOSED_TAGS: ClassVar[tuple[str, ...]]

    pdf: Incomplete
    image_map: Incomplete
    li_tag_indent: Incomplete
    table_line_separators: Incomplete
    ul_bullet_char: Incomplete
    style: Incomplete
    href: str
    align: str
    page_links: Incomplete
    font_stack: Incomplete
    indent: int
    bullet: Incomplete
    font_size: Incomplete
    font_color: Incomplete
    table: Incomplete
    table_col_width: Incomplete
    table_col_index: Incomplete
    td: Incomplete
    th: Incomplete
    tr: Incomplete
    thead: Incomplete
    tfoot: Incomplete
    tr_index: Incomplete
    theader: Incomplete
    tfooter: Incomplete
    theader_out: bool
    table_row_height: int
    heading_level: Incomplete
    heading_sizes: dict[str, int]
    heading_above: float
    heading_below: float
    pre_code_font: str
    warn_on_tags_not_matching: bool

    # Not initialized in __init__:
    font_face: Incomplete
    h: float

    def __init__(
        self,
        pdf: FPDF,
        image_map: Callable[[str], str] | None = None,
        li_tag_indent: int = 5,
        dd_tag_indent: int = 10,
        table_line_separators: bool = False,
        ul_bullet_char: str = "\x95",
        heading_sizes: SupportsKeysAndGetItem[str, int] | Iterable[tuple[str, int]] | None = None,
        pre_code_font: str = "courier",
        warn_on_tags_not_matching: bool = True,
        **_: Unused,
    ): ...
    def handle_data(self, data) -> None: ...
    def handle_starttag(self, tag, attrs) -> None: ...
    def handle_endtag(self, tag) -> None: ...
    def set_font(self, face: Incomplete | None = None, size: Incomplete | None = None) -> None: ...
    def set_style(self, tag: Incomplete | None = None, enable: bool = False) -> None: ...
    def set_text_color(self, r: Incomplete | None = None, g: int = 0, b: int = 0) -> None: ...
    def put_link(self, txt) -> None: ...
    def render_toc(self, pdf, outline) -> None: ...
    def error(self, message: str) -> None: ...

def leading_whitespace_repl(matchobj: Match[str]) -> str: ...
def whitespace_repl(matchobj: Match[str]) -> str: ...

class HTMLMixin:
    def __init__(self, *args: Incomplete, **kwargs: Incomplete) -> None: ...
