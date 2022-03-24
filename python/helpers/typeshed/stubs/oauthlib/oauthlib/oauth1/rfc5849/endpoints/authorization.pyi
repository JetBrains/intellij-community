from typing import Any

from .base import BaseEndpoint as BaseEndpoint

class AuthorizationEndpoint(BaseEndpoint):
    def create_verifier(self, request, credentials): ...
    def create_authorization_response(
        self,
        uri,
        http_method: str = ...,
        body: Any | None = ...,
        headers: Any | None = ...,
        realms: Any | None = ...,
        credentials: Any | None = ...,
    ): ...
    def get_realms_and_credentials(self, uri, http_method: str = ..., body: Any | None = ..., headers: Any | None = ...): ...
