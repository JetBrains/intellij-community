from typing import Any

from oauthlib.oauth2.rfc6749.tokens import TokenBase as TokenBase

class JWTToken(TokenBase):
    request_validator: Any
    token_generator: Any
    refresh_token_generator: Any
    expires_in: Any
    def __init__(
        self,
        request_validator: Any | None = ...,
        token_generator: Any | None = ...,
        expires_in: Any | None = ...,
        refresh_token_generator: Any | None = ...,
    ) -> None: ...
    def create_token(self, request, refresh_token: bool = ...): ...
    def validate_request(self, request): ...
    def estimate_type(self, request): ...
