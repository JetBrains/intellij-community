from typing import Any

class BaseEndpoint:
    request_validator: Any
    token_generator: Any
    def __init__(self, request_validator, token_generator: Any | None = ...) -> None: ...
