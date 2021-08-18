from typing import Any

from pygments.formatter import Formatter

class SvgFormatter(Formatter):
    name: str
    aliases: Any
    filenames: Any
    nowrap: Any
    fontfamily: Any
    fontsize: Any
    xoffset: Any
    yoffset: Any
    ystep: Any
    spacehack: Any
    linenos: Any
    linenostart: Any
    linenostep: Any
    linenowidth: Any
    def __init__(self, **options) -> None: ...
    def format_unencoded(self, tokensource, outfile) -> None: ...
