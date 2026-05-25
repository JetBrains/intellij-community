from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

class SaslHandshakeResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class SaslHandshakeResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class SaslHandshakeRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = SaslHandshakeResponse_v0
    SCHEMA: Incomplete

class SaslHandshakeRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = SaslHandshakeResponse_v1
    SCHEMA: Incomplete

SaslHandshakeRequest: Incomplete
SaslHandshakeResponse: Incomplete
