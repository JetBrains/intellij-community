from collections.abc import Iterable
from logging import Logger

from oauthlib.common import Request
from oauthlib.oauth2.rfc6749.errors import InvalidRequestError as InvalidRequestError
from oauthlib.oauth2.rfc6749.grant_types.authorization_code import AuthorizationCodeGrant as OAuth2AuthorizationCodeGrant
from oauthlib.oauth2.rfc6749.grant_types.base import _AuthValidator, _TokenValidator
from oauthlib.oauth2.rfc6749.request_validator import RequestValidator as OAuth2RequestValidator

from ..request_validator import RequestValidator
from .base import GrantTypeBase

log: Logger

class HybridGrant(GrantTypeBase):
    request_validator: OAuth2RequestValidator | RequestValidator
    proxy_target: OAuth2AuthorizationCodeGrant
    def __init__(
        self,
        request_validator: OAuth2RequestValidator | RequestValidator | None = None,
        *,
        post_auth: Iterable[_AuthValidator] | None = None,
        post_token: Iterable[_TokenValidator] | None = None,
        pre_auth: Iterable[_AuthValidator] | None = None,
        pre_token: Iterable[_TokenValidator] | None = None,
        **kwargs,
    ) -> None: ...
    def add_id_token(self, token, token_handler, request: Request): ...  # type: ignore[override]
    def openid_authorization_validator(self, request: Request): ...
