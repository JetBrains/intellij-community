from typing import Any

from .base import BaseEndpoint as BaseEndpoint

log: Any

class RevocationEndpoint(BaseEndpoint):
    valid_token_types: Any
    valid_request_methods: Any
    request_validator: Any
    supported_token_types: Any
    enable_jsonp: Any
    def __init__(self, request_validator, supported_token_types: Any | None = ..., enable_jsonp: bool = ...) -> None: ...
    def create_revocation_response(self, uri, http_method: str = ..., body: Any | None = ..., headers: Any | None = ...): ...
    def validate_revocation_request(self, request) -> None: ...
