from _typeshed import Incomplete

from .endpoint import Endpoint

class TokenEndpoint(Endpoint):
    ENDPOINT_NAME: str | None
    SUPPORTED_TOKEN_TYPES: Incomplete
    CLIENT_AUTH_METHODS: Incomplete
    def authenticate_endpoint_client(self, request): ...
    def authenticate_token(self, request, client): ...
    def create_endpoint_response(self, request): ...
