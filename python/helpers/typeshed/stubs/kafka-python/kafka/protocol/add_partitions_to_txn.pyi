from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

class AddPartitionsToTxnResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class AddPartitionsToTxnResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class AddPartitionsToTxnResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class AddPartitionsToTxnRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AddPartitionsToTxnResponse_v0
    SCHEMA: Incomplete

class AddPartitionsToTxnRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AddPartitionsToTxnResponse_v1
    SCHEMA: Incomplete

class AddPartitionsToTxnRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AddPartitionsToTxnResponse_v2
    SCHEMA: Incomplete

AddPartitionsToTxnRequest: Incomplete
AddPartitionsToTxnResponse: Incomplete
