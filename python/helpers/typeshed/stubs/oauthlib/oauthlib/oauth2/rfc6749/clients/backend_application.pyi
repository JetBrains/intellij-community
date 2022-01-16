from typing import Any

from .base import Client as Client

class BackendApplicationClient(Client):
    grant_type: str
    def prepare_request_body(self, body: str = ..., scope: Any | None = ..., include_client_id: bool = ..., **kwargs): ...  # type: ignore
