from _typeshed import Incomplete
from typing import Final

from .api import Request, Response

UNKNOWN_OFFSET: Final = -1

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
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

class ListOffsetsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

class ListOffsetsRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

class ListOffsetsRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

class ListOffsetsRequest_v4(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

class ListOffsetsRequest_v5(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    DEFAULTS: Incomplete

ListOffsetsRequest: Incomplete
ListOffsetsResponse: Incomplete
