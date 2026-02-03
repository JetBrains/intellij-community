from _typeshed import Incomplete
from typing import TypeVar

from pygments.formatter import Formatter

_T = TypeVar("_T", str, bytes)

class NullFormatter(Formatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    def format(self, tokensource, outfile) -> None: ...

class RawTokenFormatter(Formatter[bytes]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    unicodeoutput: bool
    encoding: str
    compress: Incomplete
    error_color: Incomplete
    def format(self, tokensource, outfile) -> None: ...

class TestcaseFormatter(Formatter[_T]):
    name: str
    aliases: Incomplete
    def format(self, tokensource, outfile) -> None: ...
