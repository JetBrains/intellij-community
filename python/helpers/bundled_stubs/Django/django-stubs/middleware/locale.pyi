from typing import ClassVar

from django.http.request import HttpRequest
from django.http.response import HttpResponseBase, HttpResponseRedirectBase
from django.utils.deprecation import MiddlewareMixin

class LocaleMiddleware(MiddlewareMixin):
    response_redirect_class: ClassVar[type[HttpResponseRedirectBase]]
    def process_request(self, request: HttpRequest) -> None: ...
    def process_response(self, request: HttpRequest, response: HttpResponseBase) -> HttpResponseBase: ...
