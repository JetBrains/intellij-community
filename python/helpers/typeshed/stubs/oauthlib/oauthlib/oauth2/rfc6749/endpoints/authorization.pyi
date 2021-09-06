from typing import Any

from .base import BaseEndpoint as BaseEndpoint

log: Any

class AuthorizationEndpoint(BaseEndpoint):
    def __init__(self, default_response_type, default_token_type, response_types) -> None: ...
    @property
    def response_types(self): ...
    @property
    def default_response_type(self): ...
    @property
    def default_response_type_handler(self): ...
    @property
    def default_token_type(self): ...
    def create_authorization_response(
        self,
        uri,
        http_method: str = ...,
        body: Any | None = ...,
        headers: Any | None = ...,
        scopes: Any | None = ...,
        credentials: Any | None = ...,
    ): ...
    def validate_authorization_request(self, uri, http_method: str = ..., body: Any | None = ..., headers: Any | None = ...): ...
