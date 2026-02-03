from _typeshed import Incomplete
from typing import TypeVar

from pygments.formatter import Formatter

_T = TypeVar("_T", str, bytes)

class PangoMarkupFormatter(Formatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    styles: Incomplete
    def format_unencoded(self, tokensource, outfile) -> None: ...
