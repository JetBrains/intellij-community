from typing import Any

log: Any

class Dispatcher:
    default_grant: Any
    oidc_grant: Any

class AuthorizationCodeGrantDispatcher(Dispatcher):
    default_grant: Any
    oidc_grant: Any
    def __init__(self, default_grant: Any | None = ..., oidc_grant: Any | None = ...) -> None: ...
    def create_authorization_response(self, request, token_handler): ...
    def validate_authorization_request(self, request): ...

class ImplicitTokenGrantDispatcher(Dispatcher):
    default_grant: Any
    oidc_grant: Any
    def __init__(self, default_grant: Any | None = ..., oidc_grant: Any | None = ...) -> None: ...
    def create_authorization_response(self, request, token_handler): ...
    def validate_authorization_request(self, request): ...

class AuthorizationTokenGrantDispatcher(Dispatcher):
    default_grant: Any
    oidc_grant: Any
    request_validator: Any
    def __init__(self, request_validator, default_grant: Any | None = ..., oidc_grant: Any | None = ...) -> None: ...
    def create_token_response(self, request, token_handler): ...
