from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

class OffsetCommitResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetCommitResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetCommitResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetCommitResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetCommitResponse_v4(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetCommitResponse_v5(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetCommitResponse_v6(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetCommitResponse_v7(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetCommitRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetCommitResponse_v0
    SCHEMA: Incomplete

class OffsetCommitRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetCommitResponse_v1
    SCHEMA: Incomplete

class OffsetCommitRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetCommitResponse_v2
    SCHEMA: Incomplete
    DEFAULT_RETENTION_TIME: int

class OffsetCommitRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetCommitResponse_v3
    SCHEMA: Incomplete
    DEFAULT_RETENTION_TIME: int

class OffsetCommitRequest_v4(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetCommitResponse_v4
    SCHEMA: Incomplete
    DEFAULT_RETENTION_TIME: int

class OffsetCommitRequest_v5(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetCommitResponse_v5
    SCHEMA: Incomplete

class OffsetCommitRequest_v6(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetCommitResponse_v6
    SCHEMA: Incomplete

class OffsetCommitRequest_v7(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetCommitResponse_v7
    SCHEMA: Incomplete

OffsetCommitRequest: Incomplete
OffsetCommitResponse: Incomplete

class OffsetFetchResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetFetchResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetFetchResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetFetchResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetFetchResponse_v4(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetFetchResponse_v5(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetFetchRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetFetchResponse_v0
    SCHEMA: Incomplete

class OffsetFetchRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetFetchResponse_v1
    SCHEMA: Incomplete

class OffsetFetchRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetFetchResponse_v2
    SCHEMA: Incomplete

class OffsetFetchRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetFetchResponse_v3
    SCHEMA: Incomplete

class OffsetFetchRequest_v4(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetFetchResponse_v4
    SCHEMA: Incomplete

class OffsetFetchRequest_v5(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetFetchResponse_v5
    SCHEMA: Incomplete

OffsetFetchRequest: Incomplete
OffsetFetchResponse: Incomplete
