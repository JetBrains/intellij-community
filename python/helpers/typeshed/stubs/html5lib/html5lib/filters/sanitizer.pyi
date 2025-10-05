import re
from collections.abc import Iterable
from typing_extensions import deprecated

from . import base

__all__ = ["Filter"]

allowed_elements: frozenset[tuple[str, str]]
allowed_attributes: frozenset[tuple[None, str] | tuple[str, str]]
attr_val_is_uri: frozenset[tuple[None, str] | tuple[str, str]]
svg_attr_val_allows_ref: frozenset[tuple[None, str]]
svg_allow_local_href: frozenset[tuple[None, str]]
allowed_css_properties: frozenset[str]
allowed_css_keywords: frozenset[str]
allowed_svg_properties: frozenset[str]
allowed_protocols: frozenset[str]
allowed_content_types: frozenset[str]
data_content_type: re.Pattern[str]

@deprecated("html5lib's sanitizer is deprecated; see https://github.com/html5lib/html5lib-python/issues/443")
class Filter(base.Filter):
    allowed_elements: Iterable[tuple[str | None, str]]
    allowed_attributes: Iterable[tuple[str | None, str]]
    allowed_css_properties: Iterable[str]
    allowed_css_keywords: Iterable[str]
    allowed_svg_properties: Iterable[str]
    allowed_protocols: Iterable[str]
    allowed_content_types: Iterable[str]
    attr_val_is_uri: Iterable[tuple[str | None, str]]
    svg_attr_val_allows_ref: Iterable[tuple[str | None, str]]
    svg_allow_local_href: Iterable[tuple[str | None, str]]
    def __init__(
        self,
        source,
        allowed_elements: Iterable[tuple[str | None, str]] = ...,
        allowed_attributes: Iterable[tuple[str | None, str]] = ...,
        allowed_css_properties: Iterable[str] = ...,
        allowed_css_keywords: Iterable[str] = ...,
        allowed_svg_properties: Iterable[str] = ...,
        allowed_protocols: Iterable[str] = ...,
        allowed_content_types: Iterable[str] = ...,
        attr_val_is_uri: Iterable[tuple[str | None, str]] = ...,
        svg_attr_val_allows_ref: Iterable[tuple[str | None, str]] = ...,
        svg_allow_local_href: Iterable[tuple[str | None, str]] = ...,
    ) -> None: ...
    def __iter__(self): ...
    def sanitize_token(self, token): ...
    def allowed_token(self, token): ...
    def disallowed_token(self, token): ...
    def sanitize_css(self, style: str) -> str: ...
