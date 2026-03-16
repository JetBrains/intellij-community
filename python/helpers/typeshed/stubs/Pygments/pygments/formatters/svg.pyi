from _typeshed import Incomplete
from typing import TypeVar

from pygments.formatter import Formatter

_T = TypeVar("_T", str, bytes)

class SvgFormatter(Formatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    nowrap: Incomplete
    fontfamily: Incomplete
    fontsize: Incomplete
    xoffset: Incomplete
    yoffset: Incomplete
    ystep: Incomplete
    spacehack: Incomplete
    linenos: Incomplete
    linenostart: Incomplete
    linenostep: Incomplete
    linenowidth: Incomplete
    def format_unencoded(self, tokensource, outfile) -> None: ...
