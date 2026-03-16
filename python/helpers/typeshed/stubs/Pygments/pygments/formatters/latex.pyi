from _typeshed import Incomplete
from typing import TypeVar

from pygments.formatter import Formatter
from pygments.lexer import Lexer

_T = TypeVar("_T", str, bytes)

class LatexFormatter(Formatter[_T]):
    name: str
    aliases: Incomplete
    filenames: Incomplete
    docclass: Incomplete
    preamble: Incomplete
    linenos: Incomplete
    linenostart: Incomplete
    linenostep: Incomplete
    verboptions: Incomplete
    nobackground: Incomplete
    commandprefix: Incomplete
    texcomments: Incomplete
    mathescape: Incomplete
    escapeinside: Incomplete
    left: Incomplete
    right: Incomplete
    envname: Incomplete
    def get_style_defs(self, arg: str = ""): ...
    def format_unencoded(self, tokensource, outfile) -> None: ...

class LatexEmbeddedLexer(Lexer):
    left: Incomplete
    right: Incomplete
    lang: Incomplete
    def __init__(self, left, right, lang, **options) -> None: ...
    def get_tokens_unprocessed(self, text): ...
