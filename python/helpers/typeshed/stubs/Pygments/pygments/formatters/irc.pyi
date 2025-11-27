from _typeshed import Incomplete
from typing import TypeVar

from pygments.formatter import Formatter

_T = TypeVar("_T", str, bytes)

class IRCFormatter(Formatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    darkbg: Incomplete
    colorscheme: Incomplete
    linenos: Incomplete
    def format_unencoded(self, tokensource, outfile) -> None: ...
