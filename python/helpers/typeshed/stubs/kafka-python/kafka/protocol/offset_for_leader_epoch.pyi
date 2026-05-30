from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

class OffsetForLeaderEpochResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetForLeaderEpochResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetForLeaderEpochResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetForLeaderEpochResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetForLeaderEpochResponse_v4(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class OffsetForLeaderEpochRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetForLeaderEpochResponse_v0
    SCHEMA: Incomplete

class OffsetForLeaderEpochRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetForLeaderEpochResponse_v1
    SCHEMA: Incomplete

class OffsetForLeaderEpochRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetForLeaderEpochResponse_v2
    SCHEMA: Incomplete

class OffsetForLeaderEpochRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetForLeaderEpochResponse_v3
    SCHEMA: Incomplete

class OffsetForLeaderEpochRequest_v4(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = OffsetForLeaderEpochResponse_v4
    SCHEMA: Incomplete

OffsetForLeaderEpochRequest: Incomplete
OffsetForLeaderEpochResponse: Incomplete
