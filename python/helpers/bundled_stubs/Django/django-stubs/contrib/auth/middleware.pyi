from django.contrib.auth.base_user import _UserModel
from django.contrib.auth.models import AnonymousUser
from django.http.request import HttpRequest
from django.utils.deprecation import MiddlewareMixin

def get_user(request: HttpRequest) -> AnonymousUser | _UserModel: ...
async def auser(request: HttpRequest) -> AnonymousUser | _UserModel: ...

class AuthenticationMiddleware(MiddlewareMixin):
    def process_request(self, request: HttpRequest) -> None: ...

class RemoteUserMiddleware(MiddlewareMixin):
    header: str
    force_logout_if_no_header: bool
    def process_request(self, request: HttpRequest) -> None: ...
    def clean_username(self, username: str, request: HttpRequest) -> str: ...

class PersistentRemoteUserMiddleware(RemoteUserMiddleware):
    force_logout_if_no_header: bool
