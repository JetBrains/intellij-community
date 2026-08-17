from .api_data import JsonSchemaData
from .data_container import DataContainer

class ApiHeader(DataContainer, metaclass=JsonSchemaData):
    __slots__ = ()
    def __init_subclass__(cls, **kw) -> None: ...
    def encode(self, flexible: bool = False): ...
    def encode_into(self, out, flexible: bool = False) -> None: ...
    @classmethod
    def decode(cls, data, flexible: bool = False): ...  # type: ignore[override]

class ResponseClassRegistry:
    @classmethod
    def register_response_class(cls, response_class) -> None: ...
    @classmethod
    def get_response_class(cls, request_header): ...

class RequestHeader(ApiHeader):
    def get_response_class(self): ...
    # TODO: Reflect client_id, correlation_id, request_api_key, request_api_version attributes
    def __getattr__(self, name: str): ...  # incomplete class

class ResponseHeader(ApiHeader):
    # TODO: Reflect correlation_id attribute
    def __getattr__(self, name: str): ...  # incomplete class
