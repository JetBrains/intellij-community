from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

UNKNOWN_OFFSET: int

class OffsetResetStrategy:
    LATEST: int
    EARLIEST: int
    NONE: int

class ListOffsetsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ListOffsetsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ListOffsetsResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ListOffsetsResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ListOffsetsResponse_v4(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ListOffsetsResponse_v5(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ListOffsetsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ListOffsetsResponse_v0
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

class ListOffsetsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ListOffsetsResponse_v1
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

class ListOffsetsRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ListOffsetsResponse_v2
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

class ListOffsetsRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ListOffsetsResponse_v3
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

class ListOffsetsRequest_v4(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ListOffsetsResponse_v4
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

class ListOffsetsRequest_v5(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ListOffsetsResponse_v5
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

ListOffsetsRequest: Incomplete
ListOffsetsResponse: Incomplete
