from _typeshed import Incomplete
from collections.abc import Container, Iterable, Iterator
from re import Pattern

from .callbacks import _Callback
from .html5lib_shim import Filter

DEFAULT_CALLBACKS: list[_Callback]

TLDS: list[str]

def build_url_re(tlds: Iterable[str] = ..., protocols: Iterable[str] = ...) -> Pattern[str]: ...

URL_RE: Pattern[str]
PROTO_RE: Pattern[str]

def build_email_re(tlds: Iterable[str] = ...) -> Pattern[str]: ...

EMAIL_RE: Pattern[str]

class Linker:
    def __init__(
        self,
        callbacks: Iterable[_Callback] = ...,
        skip_tags: Container[str] | None = None,
        parse_email: bool = False,
        url_re: Pattern[str] = ...,
        email_re: Pattern[str] = ...,
        recognized_tags: Container[str] | None = ...,
    ) -> None: ...
    def linkify(self, text: str) -> str: ...

class LinkifyFilter(Filter):
    callbacks: Iterable[_Callback]
    skip_tags: Container[str]
    parse_email: bool
    url_re: Pattern[str]
    email_re: Pattern[str]
    def __init__(
        self,
        source,
        callbacks: Iterable[_Callback] | None = ...,
        skip_tags: Container[str] | None = None,
        parse_email: bool = False,
        url_re: Pattern[str] = ...,
        email_re: Pattern[str] = ...,
    ) -> None: ...
    def apply_callbacks(self, attrs, is_new): ...
    def extract_character_data(self, token_list): ...
    def handle_email_addresses(self, src_iter): ...
    def strip_non_url_bits(self, fragment): ...
    def handle_links(self, src_iter): ...
    def handle_a_tag(self, token_buffer): ...
    def extract_entities(self, token): ...
    def __iter__(self) -> Iterator[Incomplete]: ...
