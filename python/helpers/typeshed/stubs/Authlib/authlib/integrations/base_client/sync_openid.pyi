from _typeshed import Incomplete
from typing import TypedDict, type_check_only

from authlib.oidc.core.claims import UserInfo

@type_check_only
class _LogoutData(TypedDict):
    url: str
    state: Incomplete

class OpenIDMixin:
    def fetch_jwk_set(self, force: bool = False): ...
    def userinfo(self, **kwargs) -> UserInfo: ...
    def parse_id_token(self, token, nonce, claims_options=None, claims_cls=None, leeway: int = 120) -> UserInfo | None: ...
    def create_logout_url(
        self, post_logout_redirect_uri=None, id_token_hint=None, state=None, *, client_id=None, logout_hint=None, ui_locales=None
    ) -> _LogoutData: ...
