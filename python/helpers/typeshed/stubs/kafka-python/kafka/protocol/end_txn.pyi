from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

class EndTxnResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class EndTxnResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class EndTxnResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class EndTxnRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = EndTxnResponse_v0
    SCHEMA: Incomplete

class EndTxnRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = EndTxnResponse_v1
    SCHEMA: Incomplete

class EndTxnRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = EndTxnResponse_v2
    SCHEMA: Incomplete

EndTxnRequest: Incomplete
EndTxnResponse: Incomplete
