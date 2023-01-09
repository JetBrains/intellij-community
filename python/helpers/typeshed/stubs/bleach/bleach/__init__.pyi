from collections.abc import Container, Iterable

from .css_sanitizer import CSSSanitizer
from .linkifier import DEFAULT_CALLBACKS as DEFAULT_CALLBACKS, Linker as Linker, _Callback
from .sanitizer import (
    ALLOWED_ATTRIBUTES as ALLOWED_ATTRIBUTES,
    ALLOWED_PROTOCOLS as ALLOWED_PROTOCOLS,
    ALLOWED_TAGS as ALLOWED_TAGS,
    Cleaner as Cleaner,
    _Attributes,
)

__all__ = ["clean", "linkify"]

__releasedate__: str
__version__: str

def clean(
    text: str,
    tags: Container[str] = ...,
    attributes: _Attributes = ...,
    protocols: Container[str] = ...,
    strip: bool = ...,
    strip_comments: bool = ...,
    css_sanitizer: CSSSanitizer | None = ...,
) -> str: ...
def linkify(
    text: str, callbacks: Iterable[_Callback] = ..., skip_tags: Container[str] | None = ..., parse_email: bool = ...
) -> str: ...
