from typing import Any

from django.contrib.sitemaps import Sitemap

class KMLSitemap(Sitemap):
    geo_format: str
    locations: Any
    def __init__(self, locations: Any | None = ...) -> None: ...
    def items(self) -> Any: ...
    def location(self, obj: Any) -> Any: ...

class KMZSitemap(KMLSitemap):
    geo_format: str
