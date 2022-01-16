from typing import Any

from .base import Client as Client

class LegacyApplicationClient(Client):
    grant_type: str
    def __init__(self, client_id, **kwargs) -> None: ...
    def prepare_request_body(  # type: ignore
        self, username, password, body: str = ..., scope: Any | None = ..., include_client_id: bool = ..., **kwargs
    ): ...
