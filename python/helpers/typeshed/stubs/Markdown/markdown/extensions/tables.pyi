from re import Pattern
from typing import ClassVar

from markdown.blockprocessors import BlockProcessor
from markdown.extensions import Extension

PIPE_NONE: int
PIPE_LEFT: int
PIPE_RIGHT: int

class TableProcessor(BlockProcessor):
    RE_CODE_PIPES: ClassVar[Pattern[str]]
    RE_END_BORDER: ClassVar[Pattern[str]]
    border: bool
    separator: str
    def __init__(self, parser, config) -> None: ...

class TableExtension(Extension): ...

def makeExtension(**kwargs): ...
