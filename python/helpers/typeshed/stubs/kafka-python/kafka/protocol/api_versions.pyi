from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

class BaseApiVersionsResponse(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    @classmethod
    def decode(cls, data): ...

class ApiVersionsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ApiVersionsResponse_v1(BaseApiVersionsResponse):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ApiVersionsResponse_v2(BaseApiVersionsResponse):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ApiVersionsResponse_v3(BaseApiVersionsResponse):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ApiVersionsResponse_v4(BaseApiVersionsResponse):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ApiVersionsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ApiVersionsResponse_v0
    SCHEMA: Incomplete

class ApiVersionsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ApiVersionsResponse_v1
    SCHEMA: Incomplete

class ApiVersionsRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ApiVersionsResponse_v2
    SCHEMA: Incomplete

class ApiVersionsRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ApiVersionsResponse_v3
    SCHEMA: Incomplete
    FLEXIBLE_VERSION: bool

class ApiVersionsRequest_v4(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ApiVersionsResponse_v4
    SCHEMA: Incomplete
    FLEXIBLE_VERSION: bool

ApiVersionsRequest: Incomplete
ApiVersionsResponse: Incomplete
