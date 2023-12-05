from _typeshed import Incomplete
from collections.abc import Iterable
from dataclasses import dataclass
from io import BytesIO
from typing_extensions import Literal

from PIL import Image

from .drawing import DeviceGray, DeviceRGB
from .enums import Align, TableBordersLayout, TableCellFillMode
from .fonts import FontFace
from .fpdf import FPDF

DEFAULT_HEADINGS_STYLE: FontFace

@dataclass(frozen=True)
class RowLayoutInfo:
    height: int
    triggers_page_jump: bool

class Table:
    rows: list[Row]

    def __init__(
        self,
        fpdf: FPDF,
        rows: Iterable[str] = (),
        *,
        align: str | Align = "CENTER",
        borders_layout: str | TableBordersLayout = ...,
        cell_fill_color: int | tuple[Incomplete, ...] | DeviceGray | DeviceRGB | None = None,
        cell_fill_mode: str | TableCellFillMode = ...,
        col_widths: int | tuple[int, ...] | None = None,
        first_row_as_headings: bool = True,
        headings_style: FontFace = ...,
        line_height: int | None = None,
        markdown=False,
        text_align: str | Align = "JUSTIFY",
        width: int | None = None,
    ) -> None: ...
    def row(self, cells: Iterable[str] = ()) -> Row: ...
    def render(self) -> None: ...
    def get_cell_border(self, i, j) -> str | Literal[0, 1]: ...

class Row:
    cells: list[Cell]
    style: FontFace
    def __init__(self, fpdf: FPDF) -> None: ...
    @property
    def cols_count(self) -> int: ...
    def cell(
        self,
        text: str = "",
        align: str | Align | None = None,
        style: FontFace | None = None,
        img: str | Image.Image | BytesIO | None = None,
        img_fill_width: bool = False,
        colspan: int = 1,
    ) -> Cell: ...

@dataclass
class Cell:
    text: str
    align: str | Align | None
    style: FontFace | None
    img: str | None
    img_fill_width: bool
    colspan: int

    def write(self, text, align: Incomplete | None = None): ...
