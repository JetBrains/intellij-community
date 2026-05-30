import abc
from _typeshed import Incomplete

from kafka.protocol.struct import Struct

class RequestHeader(Struct):
    SCHEMA: Incomplete
    def __init__(self, request, correlation_id: int = 0, client_id: str = "kafka-python") -> None: ...

class RequestHeaderV2(Struct):
    SCHEMA: Incomplete
    def __init__(self, request, correlation_id: int = 0, client_id: str = "kafka-python", tags=None) -> None: ...

class ResponseHeader(Struct):
    SCHEMA: Incomplete

class ResponseHeaderV2(Struct):
    SCHEMA: Incomplete

class Request(Struct, metaclass=abc.ABCMeta):
    FLEXIBLE_VERSION: bool
    API_KEY: Incomplete
    API_VERSION: Incomplete
    SCHEMA: Incomplete
    RESPONSE_TYPE: Incomplete
    def expect_response(self): ...
    def to_object(self): ...
    def build_header(self, correlation_id, client_id): ...

class Response(Struct, metaclass=abc.ABCMeta):
    FLEXIBLE_VERSION: bool
    API_KEY: Incomplete
    API_VERSION: Incomplete
    SCHEMA: Incomplete
    def to_object(self): ...
    @classmethod
    def parse_header(cls, read_buffer): ...
