from collections.abc import Callable
from typing import Any

from django.http.response import HttpResponseBase
from django.urls.resolvers import URLPattern

def static(prefix: str, view: Callable[..., HttpResponseBase] = ..., **kwargs: Any) -> list[URLPattern]: ...
