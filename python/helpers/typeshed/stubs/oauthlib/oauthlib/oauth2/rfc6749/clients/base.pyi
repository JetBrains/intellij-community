from typing import Any

AUTH_HEADER: str
URI_QUERY: str
BODY: str
FORM_ENC_HEADERS: Any

class Client:
    refresh_token_key: str
    client_id: Any
    default_token_placement: Any
    token_type: Any
    access_token: Any
    refresh_token: Any
    mac_key: Any
    mac_algorithm: Any
    token: Any
    scope: Any
    state_generator: Any
    state: Any
    redirect_url: Any
    code: Any
    expires_in: Any
    code_verifier: str
    code_challenge: str
    code_challenge_method: str
    def __init__(
        self,
        client_id,
        default_token_placement=...,
        token_type: str = ...,
        access_token: Any | None = ...,
        refresh_token: Any | None = ...,
        mac_key: Any | None = ...,
        mac_algorithm: Any | None = ...,
        token: Any | None = ...,
        scope: Any | None = ...,
        state: Any | None = ...,
        redirect_url: Any | None = ...,
        state_generator=...,
        code_verifier: str | None = ...,
        code_challenge: str | None = ...,
        code_challenge_method: str | None = ...,
        **kwargs,
    ) -> None: ...
    @property
    def token_types(self): ...
    def prepare_request_uri(self, *args, **kwargs) -> None: ...
    def prepare_request_body(self, *args, **kwargs) -> None: ...
    def parse_request_uri_response(self, *args, **kwargs) -> None: ...
    def add_token(
        self,
        uri,
        http_method: str = ...,
        body: Any | None = ...,
        headers: Any | None = ...,
        token_placement: Any | None = ...,
        **kwargs,
    ): ...
    def prepare_authorization_request(
        self, authorization_url, state: Any | None = ..., redirect_url: Any | None = ..., scope: Any | None = ..., **kwargs
    ): ...
    def prepare_token_request(
        self,
        token_url,
        authorization_response: Any | None = ...,
        redirect_url: Any | None = ...,
        state: Any | None = ...,
        body: str = ...,
        **kwargs,
    ): ...
    def prepare_refresh_token_request(
        self, token_url, refresh_token: Any | None = ..., body: str = ..., scope: Any | None = ..., **kwargs
    ): ...
    def prepare_token_revocation_request(
        self, revocation_url, token, token_type_hint: str = ..., body: str = ..., callback: Any | None = ..., **kwargs
    ): ...
    def parse_request_body_response(self, body, scope: Any | None = ..., **kwargs): ...
    def prepare_refresh_body(self, body: str = ..., refresh_token: Any | None = ..., scope: Any | None = ..., **kwargs): ...
    def create_code_verifier(self, length: int) -> str: ...
    def create_code_challenge(self, code_verifier: str, code_challenge_method: str | None = ...) -> str: ...
    def populate_code_attributes(self, response) -> None: ...
    def populate_token_attributes(self, response) -> None: ...
