from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from django.contrib.sitemaps import Sitemap
from django.http.request import HttpRequest
from django.template.response import TemplateResponse
from typing_extensions import TypeVar

_C = TypeVar("_C", bound=Callable[..., Any])

@dataclass
class SitemapIndexItem:
    location: str
    last_mod: bool | None = ...

def x_robots_tag(func: _C) -> _C: ...
def index(
    request: HttpRequest,
    sitemaps: dict[str, type[Sitemap[Any]] | Sitemap[Any]],
    template_name: str = ...,
    content_type: str = ...,
    sitemap_url_name: str = ...,
) -> TemplateResponse: ...
def sitemap(
    request: HttpRequest,
    sitemaps: dict[str, type[Sitemap[Any]] | Sitemap[Any]],
    section: str | None = ...,
    template_name: str = ...,
    content_type: str = ...,
) -> TemplateResponse: ...
