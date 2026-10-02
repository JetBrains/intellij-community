from _typeshed import Incomplete

from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class ApiVersionsRequest(ApiMessage):
    client_software_name: str
    client_software_version: str
    def __init__(
        self, *args, client_software_name: str = ..., client_software_version: str = ..., version: int | None = None, **kwargs
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

class ApiVersionsResponse(ApiMessage):
    class ApiVersion(DataContainer):
        api_key: int
        min_version: int
        max_version: int
        def __init__(
            self, *args, api_key: int = ..., min_version: int = ..., max_version: int = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class SupportedFeatureKey(DataContainer):
        name: str
        min_version: int
        max_version: int
        def __init__(
            self, *args, name: str = ..., min_version: int = ..., max_version: int = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class FinalizedFeatureKey(DataContainer):
        name: str
        max_version_level: int
        min_version_level: int
        def __init__(
            self,
            *args,
            name: str = ...,
            max_version_level: int = ...,
            min_version_level: int = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    error_code: int
    api_keys: list[ApiVersion]
    throttle_time_ms: int
    supported_features: list[SupportedFeatureKey]
    finalized_features_epoch: int
    finalized_features: list[FinalizedFeatureKey]
    zk_migration_ready: bool
    def __init__(
        self,
        *args,
        error_code: int = ...,
        api_keys: list[ApiVersion] = ...,
        throttle_time_ms: int = ...,
        supported_features: list[SupportedFeatureKey] = ...,
        finalized_features_epoch: int = ...,
        finalized_features: list[FinalizedFeatureKey] = ...,
        zk_migration_ready: bool = ...,
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
    @classmethod
    def parse_header(cls, data, version=None): ...
    def encode_header(self, flexible: bool = False): ...
    @classmethod
    def decode(cls, data, version=None, header: bool = False, framed: bool = False): ...  # type: ignore[override]

__all__ = ["ApiVersionsRequest", "ApiVersionsResponse"]
