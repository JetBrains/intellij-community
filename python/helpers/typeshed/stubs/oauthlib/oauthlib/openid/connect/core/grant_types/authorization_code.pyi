from typing import Any

from .base import GrantTypeBase as GrantTypeBase

log: Any

class AuthorizationCodeGrant(GrantTypeBase):
    proxy_target: Any
    def __init__(self, request_validator: Any | None = ..., **kwargs) -> None: ...
    def add_id_token(self, token, token_handler, request): ...
