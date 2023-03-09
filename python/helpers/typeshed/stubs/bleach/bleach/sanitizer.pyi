from collections.abc import Callable, Container, Iterable
from re import Pattern
from typing import Any
from typing_extensions import TypeAlias

from .css_sanitizer import CSSSanitizer
from .html5lib_shim import BleachHTMLParser, BleachHTMLSerializer, SanitizerFilter

ALLOWED_TAGS: list[str]
ALLOWED_ATTRIBUTES: dict[str, list[str]]
ALLOWED_PROTOCOLS: list[str]

INVISIBLE_CHARACTERS: str
INVISIBLE_CHARACTERS_RE: Pattern[str]
INVISIBLE_REPLACEMENT_CHAR: str

# A html5lib Filter class
_Filter: TypeAlias = Any

class Cleaner:
    tags: Container[str]
    attributes: _Attributes
    protocols: Container[str]
    strip: bool
    strip_comments: bool
    filters: Iterable[_Filter]
    css_sanitizer: CSSSanitizer | None
    parser: BleachHTMLParser
    walker: Any
    serializer: BleachHTMLSerializer
    def __init__(
        self,
        tags: Container[str] = ...,
        attributes: _Attributes = ...,
        protocols: Container[str] = ...,
        strip: bool = ...,
        strip_comments: bool = ...,
        filters: Iterable[_Filter] | None = ...,
        css_sanitizer: CSSSanitizer | None = ...,
    ) -> None: ...
    def clean(self, text: str) -> str: ...

_AttributeFilter: TypeAlias = Callable[[str, str, str], bool]
_AttributeDict: TypeAlias = dict[str, list[str] | _AttributeFilter] | dict[str, list[str]] | dict[str, _AttributeFilter]
_Attributes: TypeAlias = _AttributeFilter | _AttributeDict | list[str]

def attribute_filter_factory(attributes: _Attributes) -> _AttributeFilter: ...

class BleachSanitizerFilter(SanitizerFilter):
    attr_filter: _AttributeFilter
    strip_disallowed_elements: bool
    strip_html_comments: bool
    def __init__(
        self,
        source,
        allowed_elements: Container[str] = ...,
        attributes: _Attributes = ...,
        allowed_protocols: Container[str] = ...,
        strip_disallowed_elements: bool = ...,
        strip_html_comments: bool = ...,
        css_sanitizer: CSSSanitizer | None = ...,
        **kwargs,
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
