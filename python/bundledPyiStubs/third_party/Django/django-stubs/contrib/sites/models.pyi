from typing import Any, ClassVar

from django.db import models
from django.db.models.expressions import Combinable
from django.http.request import HttpRequest

SITE_CACHE: Any

class SiteManager(models.Manager[Site]):
    def get_current(self, request: HttpRequest | None = ...) -> Site: ...
    def clear_cache(self) -> None: ...
    def get_by_natural_key(self, domain: str) -> Site: ...

class Site(models.Model):
    objects: ClassVar[SiteManager]

    domain: models.CharField[str | int | Combinable, str]
    name: models.CharField[str | int | Combinable, str]
    def natural_key(self) -> tuple[str]: ...

def clear_site_cache(sender: type[Site], **kwargs: Any) -> None: ...
