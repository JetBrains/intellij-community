from _typeshed import Incomplete
from typing import TypeVar

from pygments.formatter import Formatter

_T = TypeVar("_T", str, bytes)

class PilNotAvailable(ImportError): ...
class FontNotFound(Exception): ...

class FontManager:
    font_name: Incomplete
    font_size: Incomplete
    fonts: Incomplete
    encoding: Incomplete
    variable: bool
    def __init__(self, font_name, font_size: int = 14) -> None: ...
    def get_char_size(self): ...
    def get_text_size(self, text): ...
    def get_font(self, bold, oblique): ...
    def get_style(self, style): ...

class ImageFormatter(Formatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    unicodeoutput: bool
    default_image_format: str
    encoding: str
    styles: Incomplete
    background_color: str
    image_format: Incomplete
    image_pad: Incomplete
    line_pad: Incomplete
    fonts: Incomplete
    line_number_fg: Incomplete
    line_number_bg: Incomplete
    line_number_chars: Incomplete
    line_number_bold: Incomplete
    line_number_italic: Incomplete
    line_number_pad: Incomplete
    line_numbers: Incomplete
    line_number_separator: Incomplete
    line_number_step: Incomplete
    line_number_start: Incomplete
    line_number_width: Incomplete
    hl_lines: Incomplete
    hl_color: Incomplete
    drawables: Incomplete
    def get_style_defs(self, arg: str = "") -> None: ...
    def format(self, tokensource, outfile) -> None: ...

class GifImageFormatter(ImageFormatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    default_image_format: str

class JpgImageFormatter(ImageFormatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    default_image_format: str

class BmpImageFormatter(ImageFormatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    default_image_format: str
