from _typeshed import Incomplete
from dataclasses import dataclass

from authlib.oauth2.rfc6749.endpoint import Endpoint, EndpointRequest
from authlib.oauth2.rfc6749.requests import OAuth2Request

@dataclass
class EndSessionRequest(EndpointRequest):
    id_token_claims: dict[Incomplete, Incomplete] | None = None
    redirect_uri: str | None = None
    logout_hint: str | None = None
    ui_locales: str | None = None
    @property
    def needs_confirmation(self) -> bool: ...

class EndSessionEndpoint(Endpoint):
    ENDPOINT_NAME: str
    def validate_request(self, request: OAuth2Request) -> EndSessionRequest: ...
    def create_response(self, validated_request: EndSessionRequest) -> tuple[int, Incomplete, list[tuple[str, str]]] | None: ...  # type: ignore[override]
    def resolve_client_from_id_token_claims(self, id_token_claims: dict[Incomplete, Incomplete]) -> Incomplete | None: ...
    def is_post_logout_redirect_uri_legitimate(
        self, request: OAuth2Request, post_logout_redirect_uri: str, client, logout_hint: str | None
    ) -> bool: ...
    def get_server_jwks(self): ...
    def get_algorithms(self) -> list[str]: ...
    def end_session(self, end_session_request: EndSessionRequest) -> None: ...
