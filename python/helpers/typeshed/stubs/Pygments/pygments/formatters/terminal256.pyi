from typing import Any

from pygments.formatter import Formatter

class EscapeSequence:
    fg: Any
    bg: Any
    bold: Any
    underline: Any
    italic: Any
    def __init__(
        self, fg: Any | None = ..., bg: Any | None = ..., bold: bool = ..., underline: bool = ..., italic: bool = ...
    ) -> None: ...
    def escape(self, attrs): ...
    def color_string(self): ...
    def true_color_string(self): ...
    def reset_string(self): ...

class Terminal256Formatter(Formatter):
    name: str
    aliases: Any
    filenames: Any
    xterm_colors: Any
    best_match: Any
    style_string: Any
    usebold: Any
    useunderline: Any
    useitalic: Any
    linenos: Any
    def __init__(self, **options) -> None: ...
    def format(self, tokensource, outfile): ...
    def format_unencoded(self, tokensource, outfile) -> None: ...

class TerminalTrueColorFormatter(Terminal256Formatter):
    name: str
    aliases: Any
    filenames: Any
