from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

class TxnOffsetCommitResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class TxnOffsetCommitResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class TxnOffsetCommitResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class TxnOffsetCommitRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = TxnOffsetCommitResponse_v0
    SCHEMA: Incomplete

class TxnOffsetCommitRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = TxnOffsetCommitResponse_v1
    SCHEMA: Incomplete

class TxnOffsetCommitRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = TxnOffsetCommitResponse_v2
    SCHEMA: Incomplete

TxnOffsetCommitRequest: Incomplete
TxnOffsetCommitResponse: Incomplete
