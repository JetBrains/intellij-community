from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

class FindCoordinatorResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FindCoordinatorResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FindCoordinatorResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class FindCoordinatorRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FindCoordinatorResponse_v0
    SCHEMA: Incomplete

class FindCoordinatorRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FindCoordinatorResponse_v1
    SCHEMA: Incomplete

class FindCoordinatorRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = FindCoordinatorResponse_v2
    SCHEMA: Incomplete

FindCoordinatorRequest: Incomplete
FindCoordinatorResponse: Incomplete
