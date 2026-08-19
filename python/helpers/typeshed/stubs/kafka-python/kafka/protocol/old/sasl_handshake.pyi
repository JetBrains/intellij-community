from _typeshed import Incomplete

from .api import Request, Response

class SaslHandshakeResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class SaslHandshakeResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class SaslHandshakeRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class SaslHandshakeRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

SaslHandshakeRequest: Incomplete
SaslHandshakeResponse: Incomplete
