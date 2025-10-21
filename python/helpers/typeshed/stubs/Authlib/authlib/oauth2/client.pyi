from _typeshed import Incomplete

from authlib.oauth2 import ClientAuth, OAuth2Error, TokenAuth

DEFAULT_HEADERS: Incomplete

class OAuth2Client:
    client_auth_class = ClientAuth
    token_auth_class = TokenAuth
    oauth_error_class = OAuth2Error
    EXTRA_AUTHORIZE_PARAMS: Incomplete
    SESSION_REQUEST_PARAMS: Incomplete
    session: Incomplete
    client_id: Incomplete
    client_secret: Incomplete
    state: Incomplete
    token_endpoint_auth_method: Incomplete
    revocation_endpoint_auth_method: Incomplete
    scope: Incomplete
    redirect_uri: Incomplete
    code_challenge_method: Incomplete
    token_auth: Incomplete
    update_token: Incomplete
    metadata: Incomplete
    compliance_hook: Incomplete
    leeway: Incomplete
    def __init__(
        self,
        session,
        client_id=None,
        client_secret=None,
        token_endpoint_auth_method=None,
        revocation_endpoint_auth_method=None,
        scope=None,
        state=None,
        redirect_uri=None,
        code_challenge_method=None,
        token=None,
        token_placement: str = "header",
        update_token=None,
        leeway: int = 60,
        **metadata,
    ) -> None: ...
    def register_client_auth_method(self, auth) -> None: ...
    def client_auth(self, auth_method): ...
    @property
    def token(self): ...
    @token.setter
    def token(self, token) -> None: ...
    def create_authorization_url(self, url, state=None, code_verifier=None, **kwargs): ...
    def fetch_token(
        self, url=None, body: str = "", method: str = "POST", headers=None, auth=None, grant_type=None, state=None, **kwargs
    ): ...
    def token_from_fragment(self, authorization_response, state=None): ...
    def refresh_token(self, url=None, refresh_token=None, body: str = "", auth=None, headers=None, **kwargs): ...
    def ensure_active_token(self, token=None): ...
    def revoke_token(self, url, token=None, token_type_hint=None, body=None, auth=None, headers=None, **kwargs): ...
    def introspect_token(self, url, token=None, token_type_hint=None, body=None, auth=None, headers=None, **kwargs): ...
    def register_compliance_hook(self, hook_type, hook) -> None: ...
    def parse_response_token(self, resp): ...
    def __del__(self) -> None: ...
