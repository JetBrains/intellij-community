from collections.abc import Generator
from typing_extensions import Never

import httpx2
from authlib.oauth1 import ClientAuth
from authlib.oauth1.client import OAuth1Client as _OAuth1Client

Auth = httpx2.Auth
Request = httpx2.Request
Response = httpx2.Response

class OAuth1Auth(Auth, ClientAuth):
    requires_request_body: bool
    def auth_flow(self, request: Request) -> Generator[Request, Response]: ...

class AsyncOAuth1Client(_OAuth1Client, httpx2.AsyncClient):  # type: ignore[misc]  # incompatible definitions of "auth" in the base classes
    auth_class = OAuth1Auth
    def __init__(
        self,
        client_id,
        client_secret=None,
        token=None,
        token_secret=None,
        redirect_uri=None,
        rsa_key=None,
        verifier=None,
        signature_method=...,
        signature_type=...,
        force_include_body=False,
        **kwargs,
    ) -> None: ...
    async def fetch_access_token(self, url, verifier=None, **kwargs): ...
    @staticmethod
    def handle_error(error_type: str | None, error_description: str | None) -> Never: ...

class OAuth1Client(_OAuth1Client, httpx2.Client):  # type: ignore[misc]  # incompatible definitions of "auth" in the base classes
    auth_class = OAuth1Auth
    def __init__(
        self,
        client_id,
        client_secret=None,
        token=None,
        token_secret=None,
        redirect_uri=None,
        rsa_key=None,
        verifier=None,
        signature_method=...,
        signature_type=...,
        force_include_body=False,
        **kwargs,
    ) -> None: ...
    @staticmethod
    def handle_error(error_type: str | None, error_description: str | None) -> Never: ...
