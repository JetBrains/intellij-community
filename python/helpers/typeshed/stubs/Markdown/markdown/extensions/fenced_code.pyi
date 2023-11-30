from _typeshed import Incomplete
from re import Pattern
from typing import ClassVar

from markdown.extensions import Extension
from markdown.preprocessors import Preprocessor

class FencedCodeExtension(Extension): ...

class FencedBlockPreprocessor(Preprocessor):
    FENCED_BLOCK_RE: ClassVar[Pattern[str]]
    codehilite_conf: dict[Incomplete, Incomplete]
    def __init__(self, md) -> None: ...

def makeExtension(**kwargs): ...
