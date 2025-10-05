from collections.abc import Callable
from typing import Any

from django.http.request import HttpRequest
from django.http.response import HttpResponse, HttpResponseBase
from django.utils.deprecation import MiddlewareMixin

class XViewMiddleware(MiddlewareMixin):
    def process_view(
        self,
        request: HttpRequest,
        view_func: Callable[..., HttpResponseBase],
        view_args: tuple[Any, ...],
        view_kwargs: dict[str, Any],
    ) -> HttpResponse | None: ...
