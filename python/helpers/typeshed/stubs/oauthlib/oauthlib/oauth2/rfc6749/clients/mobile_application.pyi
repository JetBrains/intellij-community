from typing import Any

from .base import Client as Client

class MobileApplicationClient(Client):
    response_type: str
    def prepare_request_uri(  # type: ignore[override]
        self, uri, redirect_uri: Any | None = ..., scope: Any | None = ..., state: Any | None = ..., **kwargs
    ): ...
    token: Any
    def parse_request_uri_response(self, uri, state: Any | None = ..., scope: Any | None = ...): ...  # type: ignore[override]
