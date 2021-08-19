from collections.abc import Callable, Container, Iterable
from typing import Any, Dict, List, Pattern, Union

from .html5lib_shim import BleachHTMLParser, BleachHTMLSerializer, SanitizerFilter

ALLOWED_TAGS: list[str]
ALLOWED_ATTRIBUTES: dict[str, list[str]]
ALLOWED_STYLES: list[str]
ALLOWED_PROTOCOLS: list[str]

INVISIBLE_CHARACTERS: str
INVISIBLE_CHARACTERS_RE: Pattern[str]
INVISIBLE_REPLACEMENT_CHAR: str

# A html5lib Filter class
_Filter = Any

class Cleaner(object):
    tags: Container[str]
    attributes: _Attributes
    styles: Container[str]
    protocols: Container[str]
    strip: bool
    strip_comments: bool
    filters: Iterable[_Filter]
    parser: BleachHTMLParser
    walker: Any
    serializer: BleachHTMLSerializer
    def __init__(
        self,
        tags: Container[str] = ...,
        attributes: _Attributes = ...,
        styles: Container[str] = ...,
        protocols: Container[str] = ...,
        strip: bool = ...,
        strip_comments: bool = ...,
        filters: Iterable[_Filter] | None = ...,
    ) -> None: ...
    def clean(self, text: str) -> str: ...

_AttributeFilter = Callable[[str, str, str], bool]
_AttributeDict = Union[Dict[str, Union[List[str], _AttributeFilter]], Dict[str, List[str]], Dict[str, _AttributeFilter]]
_Attributes = Union[_AttributeFilter, _AttributeDict, List[str]]

def attribute_filter_factory(attributes: _Attributes) -> _AttributeFilter: ...

class BleachSanitizerFilter(SanitizerFilter):
    attr_filter: _AttributeFilter
    strip_disallowed_elements: bool
    strip_html_comments: bool
    def __init__(
        self,
        source,
        attributes: _Attributes = ...,
        strip_disallowed_elements: bool = ...,
        strip_html_comments: bool = ...,
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
