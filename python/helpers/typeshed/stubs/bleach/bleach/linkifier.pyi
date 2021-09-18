from collections.abc import Container, Iterable, MutableMapping
from typing import Any, Pattern, Protocol

from .html5lib_shim import Filter

_Attrs = MutableMapping[Any, str]

class _Callback(Protocol):
    def __call__(self, attrs: _Attrs, new: bool = ...) -> _Attrs: ...

DEFAULT_CALLBACKS: list[_Callback]

TLDS: list[str]

def build_url_re(tlds: Iterable[str] = ..., protocols: Iterable[str] = ...) -> Pattern[str]: ...

URL_RE: Pattern[str]
PROTO_RE: Pattern[str]

def build_email_re(tlds: Iterable[str] = ...) -> Pattern[str]: ...

EMAIL_RE: Pattern[str]

class Linker(object):
    def __init__(
        self,
        callbacks: Iterable[_Callback] = ...,
        skip_tags: Container[str] | None = ...,
        parse_email: bool = ...,
        url_re: Pattern[str] = ...,
        email_re: Pattern[str] = ...,
        recognized_tags: Container[str] | None = ...,
    ) -> None: ...
    def linkify(self, text: str) -> str: ...

class LinkifyFilter(Filter):
    callbacks: Any
    skip_tags: Container[str]
    parse_email: bool
    url_re: Any
    email_re: Any
    def __init__(
        self, source, callbacks=..., skip_tags: Container[str] | None = ..., parse_email: bool = ..., url_re=..., email_re=...
    ) -> None: ...
    def __getattr__(self, item: str) -> Any: ...  # incomplete
