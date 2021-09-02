from typing import Any

from pygments.formatter import Formatter

class PangoMarkupFormatter(Formatter):
    name: str
    aliases: Any
    filenames: Any
    styles: Any
    def __init__(self, **options) -> None: ...
    def format_unencoded(self, tokensource, outfile) -> None: ...
