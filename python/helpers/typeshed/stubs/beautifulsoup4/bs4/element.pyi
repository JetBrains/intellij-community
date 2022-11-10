from _typeshed import Self
from collections.abc import Callable, Iterable, Iterator
from typing import Any, Generic, Pattern, TypeVar, overload
from typing_extensions import TypeAlias

from . import BeautifulSoup
from .builder import TreeBuilder
from .formatter import Formatter, _EntitySubstitution

DEFAULT_OUTPUT_ENCODING: str
PY3K: bool
nonwhitespace_re: Pattern[str]
whitespace_re: Pattern[str]
PYTHON_SPECIFIC_ENCODINGS: set[str]

class NamespacedAttribute(str):
    def __new__(cls: type[Self], prefix: str, name: str | None = ..., namespace: str | None = ...) -> Self: ...

class AttributeValueWithCharsetSubstitution(str): ...

class CharsetMetaAttributeValue(AttributeValueWithCharsetSubstitution):
    def __new__(cls, original_value): ...
    def encode(self, encoding: str) -> str: ...  # type: ignore[override]  # incompatible with str

class ContentMetaAttributeValue(AttributeValueWithCharsetSubstitution):
    CHARSET_RE: Pattern[str]
    def __new__(cls, original_value): ...
    def encode(self, encoding: str) -> str: ...  # type: ignore[override]  # incompatible with str

_PageElementT = TypeVar("_PageElementT", bound=PageElement)
_SimpleStrainable: TypeAlias = str | bool | None | bytes | Pattern[str] | Callable[[str], bool] | Callable[[Tag], bool]
_Strainable: TypeAlias = _SimpleStrainable | Iterable[_SimpleStrainable]
_SimpleNormalizedStrainable: TypeAlias = str | bool | None | Pattern[str] | Callable[[str], bool] | Callable[[Tag], bool]
_NormalizedStrainable: TypeAlias = _SimpleNormalizedStrainable | Iterable[_SimpleNormalizedStrainable]

class PageElement:
    parent: Tag | None
    previous_element: PageElement | None
    next_element: PageElement | None
    next_sibling: PageElement | None
    previous_sibling: PageElement | None
    def setup(
        self,
        parent: Tag | None = ...,
        previous_element: PageElement | None = ...,
        next_element: PageElement | None = ...,
        previous_sibling: PageElement | None = ...,
        next_sibling: PageElement | None = ...,
    ) -> None: ...
    def format_string(self, s: str, formatter: Formatter | str | None) -> str: ...
    def formatter_for_name(self, formatter: Formatter | str | _EntitySubstitution): ...
    nextSibling: PageElement | None
    previousSibling: PageElement | None
    @property
    def stripped_strings(self) -> Iterator[str]: ...
    def get_text(self, separator: str = ..., strip: bool = ..., types: tuple[type[NavigableString], ...] = ...) -> str: ...
    getText = get_text
    @property
    def text(self) -> str: ...
    def replace_with(self: Self, *args: PageElement | str) -> Self: ...
    replaceWith = replace_with
    def unwrap(self: Self) -> Self: ...
    replace_with_children = unwrap
    replaceWithChildren = unwrap
    def wrap(self, wrap_inside: _PageElementT) -> _PageElementT: ...
    def extract(self: Self, _self_index: int | None = ...) -> Self: ...
    def insert(self, position: int, new_child: PageElement | str) -> None: ...
    def append(self, tag: PageElement | str) -> None: ...
    def extend(self, tags: Iterable[PageElement | str]) -> None: ...
    def insert_before(self, *args: PageElement | str) -> None: ...
    def insert_after(self, *args: PageElement | str) -> None: ...
    def find_next(
        self,
        name: _Strainable | SoupStrainer | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        text: _Strainable | None = ...,
        **kwargs: _Strainable,
    ) -> Tag | NavigableString | None: ...
    findNext = find_next
    def find_all_next(
        self,
        name: _Strainable | SoupStrainer | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        text: _Strainable | None = ...,
        limit: int | None = ...,
        **kwargs: _Strainable,
    ) -> ResultSet[PageElement]: ...
    findAllNext = find_all_next
    def find_next_sibling(
        self,
        name: _Strainable | SoupStrainer | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        text: _Strainable | None = ...,
        **kwargs: _Strainable,
    ) -> Tag | NavigableString | None: ...
    findNextSibling = find_next_sibling
    def find_next_siblings(
        self,
        name: _Strainable | SoupStrainer | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        text: _Strainable | None = ...,
        limit: int | None = ...,
        **kwargs: _Strainable,
    ) -> ResultSet[PageElement]: ...
    findNextSiblings = find_next_siblings
    fetchNextSiblings = find_next_siblings
    def find_previous(
        self,
        name: _Strainable | SoupStrainer | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        text: _Strainable | None = ...,
        **kwargs: _Strainable,
    ) -> Tag | NavigableString | None: ...
    findPrevious = find_previous
    def find_all_previous(
        self,
        name: _Strainable | SoupStrainer | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        text: _Strainable | None = ...,
        limit: int | None = ...,
        **kwargs: _Strainable,
    ) -> ResultSet[PageElement]: ...
    findAllPrevious = find_all_previous
    fetchPrevious = find_all_previous
    def find_previous_sibling(
        self,
        name: _Strainable | SoupStrainer | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        text: _Strainable | None = ...,
        **kwargs: _Strainable,
    ) -> Tag | NavigableString | None: ...
    findPreviousSibling = find_previous_sibling
    def find_previous_siblings(
        self,
        name: _Strainable | SoupStrainer | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        text: _Strainable | None = ...,
        limit: int | None = ...,
        **kwargs: _Strainable,
    ) -> ResultSet[PageElement]: ...
    findPreviousSiblings = find_previous_siblings
    fetchPreviousSiblings = find_previous_siblings
    def find_parent(
        self,
        name: _Strainable | SoupStrainer | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        **kwargs: _Strainable,
    ) -> Tag | None: ...
    findParent = find_parent
    def find_parents(
        self,
        name: _Strainable | SoupStrainer | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        limit: int | None = ...,
        **kwargs: _Strainable,
    ) -> ResultSet[Tag]: ...
    findParents = find_parents
    fetchParents = find_parents
    @property
    def next(self) -> Tag | NavigableString | None: ...
    @property
    def previous(self) -> Tag | NavigableString | None: ...
    @property
    def next_elements(self) -> Iterable[PageElement]: ...
    @property
    def next_siblings(self) -> Iterable[PageElement]: ...
    @property
    def previous_elements(self) -> Iterable[PageElement]: ...
    @property
    def previous_siblings(self) -> Iterable[PageElement]: ...
    @property
    def parents(self) -> Iterable[Tag]: ...
    @property
    def decomposed(self) -> bool: ...
    def nextGenerator(self) -> Iterable[PageElement]: ...
    def nextSiblingGenerator(self) -> Iterable[PageElement]: ...
    def previousGenerator(self) -> Iterable[PageElement]: ...
    def previousSiblingGenerator(self) -> Iterable[PageElement]: ...
    def parentGenerator(self) -> Iterable[Tag]: ...

class NavigableString(str, PageElement):
    PREFIX: str
    SUFFIX: str
    known_xml: bool | None
    def __new__(cls: type[Self], value: str | bytes) -> Self: ...
    def __copy__(self: Self) -> Self: ...
    def __getnewargs__(self) -> tuple[str]: ...
    def output_ready(self, formatter: Formatter | str | None = ...) -> str: ...
    @property
    def name(self) -> None: ...
    @property
    def strings(self) -> Iterable[str]: ...

class PreformattedString(NavigableString):
    PREFIX: str
    SUFFIX: str
    def output_ready(self, formatter: Formatter | str | None = ...) -> str: ...

class CData(PreformattedString):
    PREFIX: str
    SUFFIX: str

class ProcessingInstruction(PreformattedString):
    PREFIX: str
    SUFFIX: str

class XMLProcessingInstruction(ProcessingInstruction):
    PREFIX: str
    SUFFIX: str

class Comment(PreformattedString):
    PREFIX: str
    SUFFIX: str

class Declaration(PreformattedString):
    PREFIX: str
    SUFFIX: str

class Doctype(PreformattedString):
    @classmethod
    def for_name_and_ids(cls, name: str | None, pub_id: str, system_id: str) -> Doctype: ...
    PREFIX: str
    SUFFIX: str

class Stylesheet(NavigableString): ...
class Script(NavigableString): ...
class TemplateString(NavigableString): ...

class Tag(PageElement):
    parser_class: type[BeautifulSoup] | None
    name: str
    namespace: str | None
    prefix: str | None
    sourceline: int | None
    sourcepos: int | None
    known_xml: bool | None
    attrs: dict[str, str]
    contents: list[PageElement]
    hidden: bool
    can_be_empty_element: bool | None
    cdata_list_attributes: list[str] | None
    preserve_whitespace_tags: list[str] | None
    def __init__(
        self,
        parser: BeautifulSoup | None = ...,
        builder: TreeBuilder | None = ...,
        name: str | None = ...,
        namespace: str | None = ...,
        prefix: str | None = ...,
        attrs: dict[str, str] | None = ...,
        parent: Tag | None = ...,
        previous: PageElement | None = ...,
        is_xml: bool | None = ...,
        sourceline: int | None = ...,
        sourcepos: int | None = ...,
        can_be_empty_element: bool | None = ...,
        cdata_list_attributes: list[str] | None = ...,
        preserve_whitespace_tags: list[str] | None = ...,
        interesting_string_types: type[NavigableString] | tuple[type[NavigableString], ...] | None = ...,
    ) -> None: ...
    parserClass: type[BeautifulSoup] | None
    def __copy__(self: Self) -> Self: ...
    @property
    def is_empty_element(self) -> bool: ...
    @property
    def isSelfClosing(self) -> bool: ...
    @property
    def string(self) -> str | None: ...
    @string.setter
    def string(self, string: str) -> None: ...
    DEFAULT_INTERESTING_STRING_TYPES: tuple[type[NavigableString], ...]
    @property
    def strings(self) -> Iterable[str]: ...
    def decompose(self) -> None: ...
    def clear(self, decompose: bool = ...) -> None: ...
    def smooth(self) -> None: ...
    def index(self, element: PageElement) -> int: ...
    def get(self, key: str, default: str | list[str] | None = ...) -> str | list[str] | None: ...
    def get_attribute_list(self, key: str, default: str | list[str] | None = ...) -> list[str]: ...
    def has_attr(self, key: str) -> bool: ...
    def __hash__(self) -> int: ...
    def __getitem__(self, key: str) -> str | list[str]: ...
    def __iter__(self) -> Iterable[PageElement]: ...
    def __len__(self) -> int: ...
    def __contains__(self, x: object) -> bool: ...
    def __bool__(self) -> bool: ...
    def __setitem__(self, key: str, value: str | list[str]) -> None: ...
    def __delitem__(self, key: str) -> None: ...
    def __getattr__(self, tag: str) -> Tag | None: ...
    def __eq__(self, other: object) -> bool: ...
    def __ne__(self, other: object) -> bool: ...
    def __unicode__(self) -> str: ...
    def encode(
        self, encoding: str = ..., indent_level: int | None = ..., formatter: str | Formatter = ..., errors: str = ...
    ) -> bytes: ...
    def decode(self, indent_level: int | None = ..., eventual_encoding: str = ..., formatter: str | Formatter = ...) -> str: ...
    @overload
    def prettify(self, encoding: str, formatter: str | Formatter = ...) -> bytes: ...
    @overload
    def prettify(self, encoding: None = ..., formatter: str | Formatter = ...) -> str: ...
    def decode_contents(
        self, indent_level: int | None = ..., eventual_encoding: str = ..., formatter: str | Formatter = ...
    ) -> str: ...
    def encode_contents(self, indent_level: int | None = ..., encoding: str = ..., formatter: str | Formatter = ...) -> bytes: ...
    def renderContents(self, encoding: str = ..., prettyPrint: bool = ..., indentLevel: int = ...) -> bytes: ...
    def find(
        self,
        name: _Strainable | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        recursive: bool = ...,
        text: _Strainable | None = ...,
        **kwargs: _Strainable,
    ) -> Tag | NavigableString | None: ...
    findChild = find
    def find_all(
        self,
        name: _Strainable | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        recursive: bool = ...,
        text: _Strainable | None = ...,
        limit: int | None = ...,
        **kwargs: _Strainable,
    ) -> ResultSet[Any]: ...
    __call__ = find_all
    findAll = find_all
    findChildren = find_all
    @property
    def children(self) -> Iterable[PageElement]: ...
    @property
    def descendants(self) -> Iterable[PageElement]: ...
    def select_one(self, selector: str, namespaces: Any | None = ..., **kwargs) -> Tag | None: ...
    def select(self, selector: str, namespaces: Any | None = ..., limit: int | None = ..., **kwargs) -> ResultSet[Tag]: ...
    def childGenerator(self) -> Iterable[PageElement]: ...
    def recursiveChildGenerator(self) -> Iterable[PageElement]: ...
    def has_key(self, key: str) -> bool: ...

class SoupStrainer:
    name: _NormalizedStrainable
    attrs: dict[str, _NormalizedStrainable]
    text: _NormalizedStrainable
    def __init__(
        self,
        name: _Strainable | None = ...,
        attrs: dict[str, _Strainable] | _Strainable = ...,
        text: _Strainable | None = ...,
        **kwargs: _Strainable,
    ) -> None: ...
    def search_tag(self, markup_name: Tag | str | None = ..., markup_attrs=...): ...
    searchTag = search_tag
    def search(self, markup: PageElement | Iterable[PageElement]): ...

class ResultSet(list[_PageElementT], Generic[_PageElementT]):
    source: SoupStrainer
    def __init__(self, source: SoupStrainer, result: Iterable[_PageElementT] = ...) -> None: ...
