from collections.abc import Callable
from typing import TypeVar

from django.contrib.sitemaps import Sitemap
from django.http.request import HttpRequest
from django.template.response import TemplateResponse

_C = TypeVar("_C", bound=Callable)

def x_robots_tag(func: _C) -> _C: ...
def index(
    request: HttpRequest,
    sitemaps: dict[str, type[Sitemap] | Sitemap],
    template_name: str = ...,
    content_type: str = ...,
    sitemap_url_name: str = ...,
) -> TemplateResponse: ...
def sitemap(
    request: HttpRequest,
    sitemaps: dict[str, type[Sitemap] | Sitemap],
    section: str | None = ...,
    template_name: str = ...,
    content_type: str = ...,
) -> TemplateResponse: ...
