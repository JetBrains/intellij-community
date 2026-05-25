from _typeshed import Incomplete
from typing import NamedTuple

from kafka.protocol.api import Request, Response

class AbortedTransaction(NamedTuple):
    producer_id: Incomplete
    first_offset: Incomplete

class FetchResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchResponse_v4(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchResponse_v5(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchResponse_v6(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchResponse_v7(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchResponse_v8(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchResponse_v9(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchResponse_v10(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchResponse_v11(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FetchRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v0
    SCHEMA: Incomplete

class FetchRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v1
    SCHEMA: Incomplete

class FetchRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v2
    SCHEMA: Incomplete

class FetchRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v3
    SCHEMA: Incomplete

class FetchRequest_v4(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v4
    SCHEMA: Incomplete

class FetchRequest_v5(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v5
    SCHEMA: Incomplete

class FetchRequest_v6(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v6
    SCHEMA: Incomplete

class FetchRequest_v7(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v7
    SCHEMA: Incomplete

class FetchRequest_v8(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v8
    SCHEMA: Incomplete

class FetchRequest_v9(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v9
    SCHEMA: Incomplete

class FetchRequest_v10(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v10
    SCHEMA: Incomplete

class FetchRequest_v11(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FetchResponse_v11
    SCHEMA: Incomplete

FetchRequest: Incomplete
FetchResponse: Incomplete
