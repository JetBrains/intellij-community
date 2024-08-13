from collections.abc import Iterable, Mapping, Sequence
from datetime import datetime
from typing import Any, Generic, TypeVar

from django.contrib.sites.models import Site
from django.contrib.sites.requests import RequestSite
from django.core.paginator import Paginator
from django.db.models.base import Model
from django.db.models.query import QuerySet

_ItemT = TypeVar("_ItemT")

class Sitemap(Generic[_ItemT]):
    limit: int
    protocol: str | None
    i18n: bool
    languages: Sequence[str] | None
    alternates: bool
    x_default: bool
    def items(self) -> Iterable[_ItemT]: ...
    def location(self, item: _ItemT) -> str: ...
    @property
    def paginator(self) -> Paginator: ...
    def get_languages_for_item(self, item: _ItemT) -> list[str]: ...
    def get_protocol(self, protocol: str | None = ...) -> str: ...
    def get_domain(self, site: Site | RequestSite | None = ...) -> str: ...
    def get_urls(
        self, page: int | str = ..., site: Site | RequestSite | None = ..., protocol: str | None = ...
    ) -> list[dict[str, Any]]: ...
    def get_latest_lastmod(self) -> datetime | None: ...

_ModelT = TypeVar("_ModelT", bound=Model)

class GenericSitemap(Sitemap[_ModelT]):
    priority: float | None
    changefreq: str | None
    queryset: QuerySet[_ModelT]
    date_field: str | None
    protocol: str | None
    def __init__(
        self,
        info_dict: Mapping[str, datetime | QuerySet[_ModelT] | str],
        priority: float | None = ...,
        changefreq: str | None = ...,
        protocol: str | None = ...,
    ) -> None: ...
    def items(self) -> QuerySet[_ModelT]: ...
    def lastmod(self, item: _ModelT) -> datetime | None: ...
    def get_latest_lastmod(self) -> datetime | None: ...
