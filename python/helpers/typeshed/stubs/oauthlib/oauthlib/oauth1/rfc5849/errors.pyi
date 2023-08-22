from typing import Any

class OAuth1Error(Exception):
    error: Any
    description: str
    uri: Any
    status_code: Any
    def __init__(
        self, description: Any | None = ..., uri: Any | None = ..., status_code: int = ..., request: Any | None = ...
    ) -> None: ...
    def in_uri(self, uri): ...
    @property
    def twotuples(self): ...
    @property
    def urlencoded(self): ...

class InsecureTransportError(OAuth1Error):
    error: str
    description: str

class InvalidSignatureMethodError(OAuth1Error):
    error: str

class InvalidRequestError(OAuth1Error):
    error: str

class InvalidClientError(OAuth1Error):
    error: str
