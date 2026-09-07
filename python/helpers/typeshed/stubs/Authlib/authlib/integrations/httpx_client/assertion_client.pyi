from _typeshed import Incomplete

import httpx2
from authlib.oauth2.rfc7521 import AssertionClient as _AssertionClient

from ..base_client import OAuthError
from .oauth2_client import OAuth2Auth

USE_CLIENT_DEFAULT = httpx2.USE_CLIENT_DEFAULT
Response = httpx2.Response

__all__ = ["AsyncAssertionClient"]

class AsyncAssertionClient(_AssertionClient, httpx2.AsyncClient):
    token_auth_class = OAuth2Auth
    oauth_error_class = OAuthError  # type: ignore[assignment]
    JWT_BEARER_GRANT_TYPE: Incomplete
    ASSERTION_METHODS: Incomplete
    DEFAULT_GRANT_TYPE: Incomplete
    def __init__(
        self,
        token_endpoint,
        issuer,
        subject,
        audience=None,
        grant_type=None,
        claims=None,
        token_placement="header",
        scope=None,
        client_id=None,
        **kwargs,
    ) -> None: ...
    async def request(self, method, url, withhold_token=False, auth=..., **kwargs): ...

class AssertionClient(_AssertionClient, httpx2.Client):
    token_auth_class = OAuth2Auth
    oauth_error_class = OAuthError  # type: ignore[assignment]
    JWT_BEARER_GRANT_TYPE: Incomplete
    ASSERTION_METHODS: Incomplete
    DEFAULT_GRANT_TYPE: Incomplete
    def __init__(
        self,
        token_endpoint,
        issuer,
        subject,
        audience=None,
        grant_type=None,
        claims=None,
        token_placement="header",
        scope=None,
        client_id=None,
        **kwargs,
    ) -> None: ...
    def request(self, method, url, withhold_token=False, auth=..., **kwargs): ...
