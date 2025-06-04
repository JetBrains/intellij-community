from typing import TypeVar

from django.db import models

_T = TypeVar("_T", bound=models.Model)

class CurrentSiteManager(models.Manager[_T]):
    def __init__(self, field_name: str | None = None) -> None: ...
