from _typeshed import Incomplete
from re import Pattern
from typing import Any, ClassVar
from xml.etree.ElementTree import Element

from . import util

def build_treeprocessors(md, **kwargs): ...
def isString(s): ...

class Treeprocessor(util.Processor):
    def run(self, root: Element) -> Element | None: ...

class InlineProcessor(Treeprocessor):
    inlinePatterns: Any
    ancestors: Any
    def __init__(self, md) -> None: ...
    stashed_nodes: Any
    parent_map: Any
    def run(self, tree: Element, ancestors: Incomplete | None = None) -> Element: ...

class PrettifyTreeprocessor(Treeprocessor): ...

class UnescapeTreeprocessor(Treeprocessor):
    RE: ClassVar[Pattern[str]]
    def unescape(self, text: str) -> str: ...
