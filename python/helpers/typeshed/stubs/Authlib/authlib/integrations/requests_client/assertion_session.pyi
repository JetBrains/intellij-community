from _typeshed import Incomplete
from http.cookiejar import CookieJar

from authlib.oauth2.rfc7521 import AssertionClient
from requests import Response, Session
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
from requests.cookies import RequestsCookieJar

from .oauth2_session import OAuth2Auth

class AssertionAuth(OAuth2Auth):
    def ensure_active_token(self): ...

class AssertionSession(AssertionClient, Session):
    token_auth_class = AssertionAuth
    JWT_BEARER_GRANT_TYPE: Incomplete
    ASSERTION_METHODS: Incomplete
    DEFAULT_GRANT_TYPE: Incomplete
    default_timeout: Incomplete
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
        default_timeout=None,
        leeway=60,
        **kwargs,
    ) -> None: ...
    def request(  # type: ignore[override]
        self,
        method: str,
        url: UriType,
        withhold_token: bool = False,
        auth: AuthType | None = None,
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
