from collections.abc import Container, Iterable
from typing import Any

from bleach.linkifier import DEFAULT_CALLBACKS as DEFAULT_CALLBACKS, Linker as Linker, _Callback
from bleach.sanitizer import (
    ALLOWED_ATTRIBUTES as ALLOWED_ATTRIBUTES,
    ALLOWED_PROTOCOLS as ALLOWED_PROTOCOLS,
    ALLOWED_STYLES as ALLOWED_STYLES,
    ALLOWED_TAGS as ALLOWED_TAGS,
    Cleaner as Cleaner,
    _Attributes,
)

__all__ = ["clean", "linkify"]

__releasedate__: str
__version__: str
VERSION: Any  # packaging.version.Version

def clean(
    text: str,
    tags: Container[str] = ...,
    attributes: _Attributes = ...,
    styles: Container[str] = ...,
    protocols: Container[str] = ...,
    strip: bool = ...,
    strip_comments: bool = ...,
) -> str: ...
def linkify(
    text: str, callbacks: Iterable[_Callback] = ..., skip_tags: Container[str] | None = ..., parse_email: bool = ...
) -> str: ...
