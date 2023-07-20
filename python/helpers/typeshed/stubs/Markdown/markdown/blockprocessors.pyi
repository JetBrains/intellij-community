from logging import Logger
from re import Match, Pattern
from typing import Any
from xml.etree.ElementTree import Element

from markdown import Markdown

from .blockparser import BlockParser

logger: Logger

def build_block_parser(md: Markdown, **kwargs: Any): ...

class BlockProcessor:
    parser: BlockParser
    tab_length: int
    def __init__(self, parser: BlockParser) -> None: ...
    def lastChild(self, parent: Element) -> Element | None: ...
    def detab(self, text: str, length: int | None = ...) -> tuple[str, str]: ...
    def looseDetab(self, text: str, level: int = ...) -> str: ...
    def test(self, parent: Element, block: str) -> bool: ...
    def run(self, parent: Element, blocks: list[str]) -> bool | None: ...

class ListIndentProcessor(BlockProcessor):
    ITEM_TYPES: list[str]
    LIST_TYPES: list[str]
    INDENT_RE: Pattern[str]
    def __init__(self, parser: BlockParser) -> None: ...  # Note: This was done because the args are sent as-is.
    def create_item(self, parent: Element, block: str) -> None: ...
    def get_level(self, parent: Element, block: str) -> tuple[int, Element]: ...

class CodeBlockProcessor(BlockProcessor): ...

class BlockQuoteProcessor(BlockProcessor):
    RE: Pattern[str]
    def clean(self, line: str) -> str: ...

class OListProcessor(BlockProcessor):
    TAG: str = ...
    STARTSWITH: str = ...
    LAZY_OL: bool = ...
    SIBLING_TAGS: list[str]
    RE: Pattern[str]
    CHILD_RE: Pattern[str]
    INDENT_RE: Pattern[str]
    def __init__(self, parser: BlockParser) -> None: ...
    def get_items(self, block: str) -> list[str]: ...

class UListProcessor(OListProcessor):
    TAG: str = ...
    RE: Pattern[str]
    def __init__(self, parser: BlockParser) -> None: ...

class HashHeaderProcessor(BlockProcessor):
    RE: Pattern[str]

class SetextHeaderProcessor(BlockProcessor):
    RE: Pattern[str]

class HRProcessor(BlockProcessor):
    RE: str = ...
    SEARCH_RE: Pattern[str]
    match: Match[str]

class EmptyBlockProcessor(BlockProcessor): ...

class ReferenceProcessor(BlockProcessor):
    RE: Pattern[str]

class ParagraphProcessor(BlockProcessor): ...
