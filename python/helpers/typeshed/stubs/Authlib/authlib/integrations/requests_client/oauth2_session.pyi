from _typeshed import Incomplete
from http.cookiejar import CookieJar

from authlib.oauth2.auth import ClientAuth, TokenAuth
from authlib.oauth2.client import OAuth2Client
from requests import PreparedRequest, Response, Session
from requests._types import (
    AuthType,
    CertType,
    DataType,
    FilesType,
    HeadersType,
    HooksInputType,
    JsonType,
    ParamsType,
    TimeoutType,
    UriType,
    VerifyType,
)
from requests.auth import AuthBase
from requests.cookies import RequestsCookieJar

from ..base_client import OAuthError

__all__ = ["OAuth2Session", "OAuth2Auth"]

class OAuth2Auth(AuthBase, TokenAuth):
    def ensure_active_token(self) -> None: ...
    def __call__(self, req: PreparedRequest) -> PreparedRequest: ...

class OAuth2ClientAuth(AuthBase, ClientAuth):
    def __call__(self, req: PreparedRequest) -> PreparedRequest: ...

class OAuth2Session(OAuth2Client, Session):
    client_auth_class = OAuth2ClientAuth
    token_auth_class = OAuth2Auth
    oauth_error_class = OAuthError  # type: ignore[assignment]
    SESSION_REQUEST_PARAMS: tuple[str, ...]  # type: ignore[assignment]
    default_timeout: Incomplete
    def __init__(
        self,
        client_id=None,
        client_secret=None,
        token_endpoint_auth_method=None,
        revocation_endpoint_auth_method=None,
        scope=None,
        state=None,
        redirect_uri=None,
        token=None,
        token_placement="header",
        update_token=None,
        leeway=60,
        default_timeout=None,
        **kwargs,
    ) -> None: ...
    def fetch_access_token(self, url=None, **kwargs): ...
    def request(  # type: ignore[override]
        self,
        method: str,
        url: UriType,
        withhold_token: bool = False,
        auth: AuthType = None,
        *,
        params: ParamsType = None,
        data: DataType = None,
        headers: HeadersType = None,
        cookies: RequestsCookieJar | CookieJar | dict[str, str] | None = None,
        files: FilesType = None,
        timeout: TimeoutType = None,
        allow_redirects: bool = True,
        proxies: dict[str, str] | None = None,
        hooks: HooksInputType | None = None,
        stream: bool | None = None,
        verify: VerifyType | None = None,
        cert: CertType = None,
        json: JsonType = None,
    ) -> Response: ...
