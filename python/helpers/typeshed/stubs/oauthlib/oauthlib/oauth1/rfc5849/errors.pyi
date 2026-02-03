from typing import Any

class OAuth1Error(Exception):
    error: Any
    description: str
    uri: Any
    status_code: Any
    def __init__(self, description=None, uri=None, status_code: int = 400, request=None) -> None: ...
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
