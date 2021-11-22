from typing import Any

from pygments.formatter import Formatter

class IRCFormatter(Formatter):
    name: str
    aliases: Any
    filenames: Any
    darkbg: Any
    colorscheme: Any
    linenos: Any
    def __init__(self, **options) -> None: ...
    def format_unencoded(self, tokensource, outfile) -> None: ...
