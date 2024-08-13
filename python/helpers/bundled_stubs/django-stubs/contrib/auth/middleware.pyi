from django.contrib.auth.base_user import AbstractBaseUser
from django.contrib.auth.models import AnonymousUser
from django.http.request import HttpRequest
from django.utils.deprecation import MiddlewareMixin

def get_user(request: HttpRequest) -> AnonymousUser | AbstractBaseUser: ...
async def auser(request: HttpRequest) -> AnonymousUser | AbstractBaseUser: ...

class AuthenticationMiddleware(MiddlewareMixin):
    def process_request(self, request: HttpRequest) -> None: ...

class RemoteUserMiddleware(MiddlewareMixin):
    header: str
    force_logout_if_no_header: bool
    def process_request(self, request: HttpRequest) -> None: ...
    def clean_username(self, username: str, request: HttpRequest) -> str: ...

class PersistentRemoteUserMiddleware(RemoteUserMiddleware):
    force_logout_if_no_header: bool
