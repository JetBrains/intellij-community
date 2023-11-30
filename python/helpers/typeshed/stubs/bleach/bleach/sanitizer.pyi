from _typeshed import Incomplete
from collections.abc import Callable, Iterable
from re import Pattern
from typing import Protocol
from typing_extensions import TypeAlias

from . import _HTMLAttrKey
from .css_sanitizer import CSSSanitizer
from .html5lib_shim import BleachHTMLParser, BleachHTMLSerializer, SanitizerFilter

ALLOWED_TAGS: frozenset[str]
ALLOWED_ATTRIBUTES: dict[str, list[str]]
ALLOWED_PROTOCOLS: frozenset[str]

INVISIBLE_CHARACTERS: str
INVISIBLE_CHARACTERS_RE: Pattern[str]
INVISIBLE_REPLACEMENT_CHAR: str

# A html5lib Filter class
class _Filter(Protocol):
    def __call__(self, *, source: BleachSanitizerFilter) -> Incomplete: ...

_AttributeFilter: TypeAlias = Callable[[str, str, str], bool]
_AttributeDict: TypeAlias = dict[str, list[str] | _AttributeFilter] | dict[str, list[str]] | dict[str, _AttributeFilter]
_Attributes: TypeAlias = _AttributeFilter | _AttributeDict | list[str]

_TreeWalker: TypeAlias = Callable[[Incomplete], Incomplete]

class Cleaner:
    tags: Iterable[str]
    attributes: _Attributes
    protocols: Iterable[str]
    strip: bool
    strip_comments: bool
    filters: Iterable[_Filter]
    css_sanitizer: CSSSanitizer | None
    parser: BleachHTMLParser
    walker: _TreeWalker
    serializer: BleachHTMLSerializer
    def __init__(
        self,
        tags: Iterable[str] = ...,
        attributes: _Attributes = ...,
        protocols: Iterable[str] = ...,
        strip: bool = False,
        strip_comments: bool = True,
        filters: Iterable[_Filter] | None = None,
        css_sanitizer: CSSSanitizer | None = None,
    ) -> None: ...
    def clean(self, text: str) -> str: ...

def attribute_filter_factory(attributes: _Attributes) -> _AttributeFilter: ...

class BleachSanitizerFilter(SanitizerFilter):
    allowed_tags: frozenset[str]
    allowed_protocols: frozenset[str]
    attr_filter: _AttributeFilter
    strip_disallowed_tags: bool
    strip_html_comments: bool
    attr_val_is_uri: frozenset[_HTMLAttrKey]
    svg_attr_val_allows_ref: frozenset[_HTMLAttrKey]
    svg_allow_local_href: frozenset[_HTMLAttrKey]
    css_sanitizer: CSSSanitizer | None
    def __init__(
        self,
        source,
        allowed_tags: Iterable[str] = ...,
        attributes: _Attributes = ...,
        allowed_protocols: Iterable[str] = ...,
        attr_val_is_uri: frozenset[_HTMLAttrKey] = ...,
        svg_attr_val_allows_ref: frozenset[_HTMLAttrKey] = ...,
        svg_allow_local_href: frozenset[_HTMLAttrKey] = ...,
        strip_disallowed_tags: bool = False,
        strip_html_comments: bool = True,
        css_sanitizer: CSSSanitizer | None = None,
    ) -> None: ...
    def sanitize_stream(self, token_iterator): ...
    def merge_characters(self, token_iterator): ...
    def __iter__(self): ...
    def sanitize_token(self, token): ...
    def sanitize_characters(self, token): ...
    def sanitize_uri_value(self, value, allowed_protocols): ...
    def allow_token(self, token): ...
    def disallowed_token(self, token): ...
    def sanitize_css(self, style): ...
