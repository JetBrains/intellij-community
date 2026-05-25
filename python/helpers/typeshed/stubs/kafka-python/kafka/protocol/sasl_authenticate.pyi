from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

class SaslAuthenticateResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class SaslAuthenticateResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class SaslAuthenticateRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = SaslAuthenticateResponse_v0
    SCHEMA: Incomplete

class SaslAuthenticateRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = SaslAuthenticateResponse_v1
    SCHEMA: Incomplete

SaslAuthenticateRequest: Incomplete
SaslAuthenticateResponse: Incomplete
