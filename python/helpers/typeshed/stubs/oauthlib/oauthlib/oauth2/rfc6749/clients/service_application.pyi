from typing import Any

from .base import Client as Client

class ServiceApplicationClient(Client):
    grant_type: str
    private_key: Any
    subject: Any
    issuer: Any
    audience: Any
    def __init__(
        self,
        client_id,
        private_key: Any | None = ...,
        subject: Any | None = ...,
        issuer: Any | None = ...,
        audience: Any | None = ...,
        **kwargs,
    ) -> None: ...
    def prepare_request_body(  # type: ignore
        self,
        private_key: Any | None = ...,
        subject: Any | None = ...,
        issuer: Any | None = ...,
        audience: Any | None = ...,
        expires_at: Any | None = ...,
        issued_at: Any | None = ...,
        extra_claims: Any | None = ...,
        body: str = ...,
        scope: Any | None = ...,
        include_client_id: bool = ...,
        **kwargs,
    ): ...
