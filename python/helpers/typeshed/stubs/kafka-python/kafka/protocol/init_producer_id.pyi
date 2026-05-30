from _typeshed import Incomplete

from kafka.protocol.api import Request, Response

class InitProducerIdResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class InitProducerIdResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class InitProducerIdRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = InitProducerIdResponse_v0
    SCHEMA: Incomplete

class InitProducerIdRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = InitProducerIdResponse_v1
    SCHEMA: Incomplete

InitProducerIdRequest: Incomplete
InitProducerIdResponse: Incomplete
