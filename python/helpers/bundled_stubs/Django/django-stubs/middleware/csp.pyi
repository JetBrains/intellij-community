from django.http import HttpRequest, HttpResponse
from django.utils.csp import CSP as CSP
from django.utils.csp import LazyNonce
from django.utils.deprecation import MiddlewareMixin

def get_nonce(request: HttpRequest) -> LazyNonce | None: ...

class ContentSecurityPolicyMiddleware(MiddlewareMixin):
    def process_request(self, request: HttpRequest) -> None: ...
    def process_response(self, request: HttpRequest, response: HttpResponse) -> HttpResponse: ...
