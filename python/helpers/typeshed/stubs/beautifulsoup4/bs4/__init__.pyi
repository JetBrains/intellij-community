from _typeshed import Self, SupportsRead
from typing import Any, Sequence, Type

from .builder import TreeBuilder
from .element import PageElement, SoupStrainer, Tag
from .formatter import Formatter

class GuessedAtParserWarning(UserWarning): ...
class MarkupResemblesLocatorWarning(UserWarning): ...

class BeautifulSoup(Tag):
    ROOT_TAG_NAME: str
    DEFAULT_BUILDER_FEATURES: list[str]
    ASCII_SPACES: str
    NO_PARSER_SPECIFIED_WARNING: str
    element_classes: Any
    builder: TreeBuilder
    is_xml: bool
    known_xml: bool
    parse_only: SoupStrainer | None
    markup: str
    def __init__(
        self,
        markup: str | bytes | SupportsRead[str] | SupportsRead[bytes] = ...,
        features: str | Sequence[str] | None = ...,
        builder: TreeBuilder | Type[TreeBuilder] | None = ...,
        parse_only: SoupStrainer | None = ...,
        from_encoding: str | None = ...,
        exclude_encodings: Sequence[str] | None = ...,
        element_classes: dict[Type[PageElement], Type[Any]] | None = ...,
        **kwargs,
    ) -> None: ...
    def __copy__(self: Self) -> Self: ...
    hidden: bool
    current_data: Any
    currentTag: Any
    tagStack: Any
    open_tag_counter: Any
    preserve_whitespace_tag_stack: Any
    string_container_stack: Any
    def reset(self) -> None: ...
    def new_tag(
        self,
        name,
        namespace: Any | None = ...,
        nsprefix: Any | None = ...,
        attrs=...,
        sourceline: Any | None = ...,
        sourcepos: Any | None = ...,
        **kwattrs,
    ) -> Tag: ...
    def string_container(self, base_class: Any | None = ...): ...
    def new_string(self, s, subclass: Any | None = ...): ...
    def insert_before(self, *args) -> None: ...
    def insert_after(self, *args) -> None: ...
    def popTag(self): ...
    def pushTag(self, tag) -> None: ...
    def endData(self, containerClass: Any | None = ...) -> None: ...
    def object_was_parsed(self, o, parent: Any | None = ..., most_recent_element: Any | None = ...) -> None: ...
    def handle_starttag(self, name, namespace, nsprefix, attrs, sourceline: Any | None = ..., sourcepos: Any | None = ...): ...
    def handle_endtag(self, name, nsprefix: Any | None = ...) -> None: ...
    def handle_data(self, data) -> None: ...
    def decode(self, pretty_print: bool = ..., eventual_encoding: str = ..., formatter: str | Formatter = ...): ...  # type: ignore  # missing some arguments

class BeautifulStoneSoup(BeautifulSoup): ...
class StopParsing(Exception): ...
class FeatureNotFound(ValueError): ...
