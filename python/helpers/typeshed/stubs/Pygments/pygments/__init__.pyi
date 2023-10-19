from _typeshed import SupportsWrite
from typing import TypeVar, overload

from pygments.formatter import Formatter

_T = TypeVar("_T", str, bytes)

__version__: str
__all__ = ["lex", "format", "highlight"]

def lex(code, lexer): ...
@overload
def format(tokens, formatter: Formatter[_T], outfile: SupportsWrite[_T]) -> None: ...
@overload
def format(tokens, formatter: Formatter[_T], outfile: None = None) -> _T: ...
@overload
def highlight(code, lexer, formatter: Formatter[_T], outfile: SupportsWrite[_T]) -> None: ...
@overload
def highlight(code, lexer, formatter: Formatter[_T], outfile: None = None) -> _T: ...
