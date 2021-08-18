from typing import Any

from pygments.formatter import Formatter

class NullFormatter(Formatter):
    name: str
    aliases: Any
    filenames: Any
    def format(self, tokensource, outfile) -> None: ...

class RawTokenFormatter(Formatter):
    name: str
    aliases: Any
    filenames: Any
    unicodeoutput: bool
    encoding: str
    compress: Any
    error_color: Any
    def __init__(self, **options) -> None: ...
    def format(self, tokensource, outfile) -> None: ...

class TestcaseFormatter(Formatter):
    name: str
    aliases: Any
    def __init__(self, **options) -> None: ...
    def format(self, tokensource, outfile) -> None: ...
