from _typeshed import Incomplete
from typing import TypeVar

from pygments.formatter import Formatter

_T = TypeVar("_T", str, bytes)

class RtfFormatter(Formatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    fontface: Incomplete
    fontsize: Incomplete
    def format_unencoded(self, tokensource, outfile) -> None: ...
