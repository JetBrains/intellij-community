from typing import Any

from django.contrib.sitemaps import Sitemap
from typing_extensions import override

class KMLSitemap(Sitemap):
    geo_format: str
    locations: Any
    def __init__(self, locations: Any | None = ...) -> None: ...
    @override
    def items(self) -> Any: ...
    @override
    def location(self, obj: Any) -> Any: ...

class KMZSitemap(KMLSitemap):
    geo_format: str
