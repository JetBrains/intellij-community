from typing import Any

from .base import BaseEndpoint as BaseEndpoint

log: Any

class IntrospectEndpoint(BaseEndpoint):
    valid_token_types: Any
    valid_request_methods: Any
    request_validator: Any
    supported_token_types: Any
    def __init__(self, request_validator, supported_token_types: Any | None = ...) -> None: ...
    def create_introspect_response(self, uri, http_method: str = ..., body: Any | None = ..., headers: Any | None = ...): ...
    def validate_introspect_request(self, request) -> None: ...
