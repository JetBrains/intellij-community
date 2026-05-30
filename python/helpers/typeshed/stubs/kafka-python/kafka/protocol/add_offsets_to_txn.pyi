from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

class AddOffsetsToTxnResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class AddOffsetsToTxnResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class AddOffsetsToTxnResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class AddOffsetsToTxnRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AddOffsetsToTxnResponse_v0
    SCHEMA: Incomplete

class AddOffsetsToTxnRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AddOffsetsToTxnResponse_v1
    SCHEMA: Incomplete

class AddOffsetsToTxnRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AddOffsetsToTxnResponse_v2
    SCHEMA: Incomplete

AddOffsetsToTxnRequest: Incomplete
AddOffsetsToTxnResponse: Incomplete
