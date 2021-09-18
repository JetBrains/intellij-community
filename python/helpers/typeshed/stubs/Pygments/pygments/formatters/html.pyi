from typing import Any

from pygments.formatter import Formatter

class HtmlFormatter(Formatter):
    name: str
    aliases: Any
    filenames: Any
    title: Any
    nowrap: Any
    noclasses: Any
    classprefix: Any
    cssclass: Any
    cssstyles: Any
    prestyles: Any
    cssfile: Any
    noclobber_cssfile: Any
    tagsfile: Any
    tagurlformat: Any
    filename: Any
    wrapcode: Any
    span_element_openers: Any
    linenos: int
    linenostart: Any
    linenostep: Any
    linenospecial: Any
    nobackground: Any
    lineseparator: Any
    lineanchors: Any
    linespans: Any
    anchorlinenos: Any
    hl_lines: Any
    def __init__(self, **options) -> None: ...
    def get_style_defs(self, arg: Any | None = ...): ...
    def get_token_style_defs(self, arg: Any | None = ...): ...
    def get_background_style_defs(self, arg: Any | None = ...): ...
    def get_linenos_style_defs(self): ...
    def get_css_prefix(self, arg): ...
    def wrap(self, source, outfile): ...
    def format_unencoded(self, tokensource, outfile) -> None: ...
