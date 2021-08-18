from typing import Any

from .base import BaseEndpoint as BaseEndpoint

log: Any

class ResourceEndpoint(BaseEndpoint):
    def validate_protected_resource_request(
        self, uri, http_method: str = ..., body: Any | None = ..., headers: Any | None = ..., realms: Any | None = ...
    ): ...
