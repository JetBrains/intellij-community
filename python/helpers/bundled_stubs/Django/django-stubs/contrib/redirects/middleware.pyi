from typing import ClassVar

from django.http.request import HttpRequest
from django.http.response import HttpResponse, HttpResponseBase, HttpResponseRedirectBase
from django.utils.deprecation import MiddlewareMixin

class RedirectFallbackMiddleware(MiddlewareMixin):
    response_gone_class: ClassVar[type[HttpResponseBase]]
    response_redirect_class: ClassVar[type[HttpResponseRedirectBase]]
    def process_response(self, request: HttpRequest, response: HttpResponse) -> HttpResponse: ...
