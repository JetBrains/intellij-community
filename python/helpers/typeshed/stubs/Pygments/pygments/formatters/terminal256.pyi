from _typeshed import Incomplete
from typing import TypeVar

from pygments.formatter import Formatter

_T = TypeVar("_T", str, bytes)

class EscapeSequence:
    fg: Incomplete
    bg: Incomplete
    bold: Incomplete
    underline: Incomplete
    italic: Incomplete
    def __init__(self, fg=None, bg=None, bold: bool = False, underline: bool = False, italic: bool = False) -> None: ...
    def escape(self, attrs): ...
    def color_string(self): ...
    def true_color_string(self): ...
    def reset_string(self): ...

class Terminal256Formatter(Formatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    xterm_colors: Incomplete
    best_match: Incomplete
    style_string: Incomplete
    usebold: Incomplete
    useunderline: Incomplete
    useitalic: Incomplete
    linenos: Incomplete
    def format(self, tokensource, outfile): ...
    def format_unencoded(self, tokensource, outfile) -> None: ...

class TerminalTrueColorFormatter(Terminal256Formatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
