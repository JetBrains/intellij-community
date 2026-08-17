from _typeshed import Incomplete
from typing_extensions import Self

from kafka.protocol.api_data import ApiData
from kafka.protocol.api_message import ApiMessage

class SaslHandshakeRequest(ApiMessage):
    mechanism: str
    def __init__(self, *args, mechanism: str = ..., version: int | None = None, **kwargs) -> None: ...
    @property
    def version(self) -> int | None: ...
    def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...
    name: str
    type: str
    API_KEY: int
    API_VERSION: int
    valid_versions: tuple[int, int]
    min_version: int
    max_version: int
    @property
    def header(self): ...
    @classmethod
    def is_request(cls) -> bool: ...
    def expect_response(self) -> bool: ...
    def with_header(self, correlation_id: int = 0, client_id: str = "kafka-python") -> None: ...

class SaslHandshakeResponse(ApiMessage):
    error_code: int
    mechanisms: list[str]
    def __init__(
        self, *args, error_code: int = ..., mechanisms: list[str] = ..., version: int | None = None, **kwargs
    ) -> None: ...
    @property
    def version(self) -> int | None: ...
    def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...
    name: str
    type: str
    API_KEY: int
    API_VERSION: int
    valid_versions: tuple[int, int]
    min_version: int
    max_version: int
    @property
    def header(self): ...
    @classmethod
    def is_request(cls) -> bool: ...
    def expect_response(self) -> bool: ...
    def with_header(self, correlation_id: int = 0, client_id: str = "kafka-python") -> None: ...

class SaslAuthenticateRequest(ApiMessage):
    auth_bytes: bytes | ApiData
    def __init__(self, *args, auth_bytes: bytes | ApiData = ..., version: int | None = None, **kwargs) -> None: ...
    @property
    def version(self) -> int | None: ...
    def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...
    name: str
    type: str
    API_KEY: int
    API_VERSION: int
    valid_versions: tuple[int, int]
    min_version: int
    max_version: int
    @property
    def header(self): ...
    @classmethod
    def is_request(cls) -> bool: ...
    def expect_response(self) -> bool: ...
    def with_header(self, correlation_id: int = 0, client_id: str = "kafka-python") -> None: ...

class SaslAuthenticateResponse(ApiMessage):
    error_code: int
    error_message: str | None
    auth_bytes: bytes | ApiData
    session_lifetime_ms: int
    def __init__(
        self,
        *args,
        error_code: int = ...,
        error_message: str | None = ...,
        auth_bytes: bytes | ApiData = ...,
        session_lifetime_ms: int = ...,
        version: int | None = None,
        **kwargs,
    ) -> None: ...
    @property
    def version(self) -> int | None: ...
    def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...
    name: str
    type: str
    API_KEY: int
    API_VERSION: int
    valid_versions: tuple[int, int]
    min_version: int
    max_version: int
    @property
    def header(self): ...
    @classmethod
    def is_request(cls) -> bool: ...
    def expect_response(self) -> bool: ...
    def with_header(self, correlation_id: int = 0, client_id: str = "kafka-python") -> None: ...

class SaslBytesRequest:
    API_VERSION: int
    header: SaslBytesResponse | None
    def __init__(self, data) -> None: ...
    def with_header(self, correlation_id=None, **kwargs) -> None: ...
    def encode(self, framed: bool = True, header: bool = True) -> bytes: ...
    def expect_response(self): ...

class SaslBytesResponse:
    correlation_id: Incomplete
    error_code: int
    def __init__(self, correlation_id) -> None: ...
    def parse_header(self, read_buffer) -> Self: ...
    auth_bytes: Incomplete
    def decode(self, read_buffer) -> Self: ...
    def get_response_class(self) -> Self: ...

__all__ = [
    "SaslHandshakeRequest",
    "SaslHandshakeResponse",
    "SaslAuthenticateRequest",
    "SaslAuthenticateResponse",
    "SaslBytesRequest",
    "SaslBytesResponse",
]
