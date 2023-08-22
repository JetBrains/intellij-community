from typing import Any

from .base import Client as Client

class WebApplicationClient(Client):
    grant_type: str
    code: Any
    def __init__(self, client_id, code: Any | None = ..., **kwargs) -> None: ...
    def prepare_request_uri(  # type: ignore[override]
        self,
        uri,
        redirect_uri: Any | None = ...,
        scope: Any | None = ...,
        state: Any | None = ...,
        code_challenge: str | None = ...,
        code_challenge_method: str | None = ...,
        **kwargs,
    ): ...
    def prepare_request_body(  # type: ignore[override]
        self,
        code: Any | None = ...,
        redirect_uri: Any | None = ...,
        body: str = ...,
        include_client_id: bool = ...,
        code_verifier: str | None = ...,
        **kwargs,
    ): ...
    def parse_request_uri_response(self, uri, state: Any | None = ...): ...  # type: ignore[override]
