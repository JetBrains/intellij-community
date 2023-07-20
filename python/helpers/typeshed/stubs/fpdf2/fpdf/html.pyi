from html.parser import HTMLParser
from typing import Any

LOGGER: Any
BULLET_WIN1252: str
DEFAULT_HEADING_SIZES: Any
COLOR_DICT: Any

def px2mm(px): ...
def color_as_decimal(color: str = ...): ...

class HTML2FPDF(HTMLParser):
    pdf: Any
    image_map: Any
    li_tag_indent: Any
    table_line_separators: Any
    ul_bullet_char: Any
    style: Any
    href: str
    align: str
    page_links: Any
    font_stack: Any
    indent: int
    bullet: Any
    font_size: Any
    font_color: Any
    table: Any
    table_col_width: Any
    table_col_index: Any
    td: Any
    th: Any
    tr: Any
    thead: Any
    tfoot: Any
    tr_index: Any
    theader: Any
    tfooter: Any
    theader_out: bool
    table_row_height: int
    heading_level: Any
    heading_sizes: Any
    heading_above: float
    heading_below: float
    def __init__(
        self,
        pdf,
        image_map: Any | None = ...,
        li_tag_indent: int = ...,
        table_line_separators: bool = ...,
        ul_bullet_char=...,
        heading_sizes: Any | None = ...,
        **_,
    ): ...
    def width2unit(self, length): ...
    def handle_data(self, data) -> None: ...
    def box_shadow(self, w, h, bgcolor) -> None: ...
    def output_table_header(self) -> None: ...
    tfooter_out: bool
    def output_table_footer(self) -> None: ...
    def output_table_sep(self) -> None: ...
    font_face: Any
    table_offset: Any
    def handle_starttag(self, tag, attrs) -> None: ...
    tbody: Any
    def handle_endtag(self, tag) -> None: ...
    h: Any
    def set_font(self, face: Any | None = ..., size: Any | None = ...) -> None: ...
    def set_style(self, tag: Any | None = ..., enable: bool = ...) -> None: ...
    def set_text_color(self, r: Any | None = ..., g: int = ..., b: int = ...) -> None: ...
    def put_link(self, txt) -> None: ...
    def render_toc(self, pdf, outline) -> None: ...
    def error(self, message: str) -> None: ...

class HTMLMixin:
    HTML2FPDF_CLASS: Any
    def write_html(self, text, *args, **kwargs) -> None: ...
