from typing import Any

from pygments.formatter import Formatter

class RtfFormatter(Formatter):
    name: str
    aliases: Any
    filenames: Any
    fontface: Any
    fontsize: Any
    def __init__(self, **options) -> None: ...
    def format_unencoded(self, tokensource, outfile) -> None: ...
