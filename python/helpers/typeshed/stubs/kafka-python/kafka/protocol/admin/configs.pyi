from _typeshed import Incomplete

from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class AlterConfigsRequest(ApiMessage):
    class AlterConfigsResource(DataContainer):
        class AlterableConfig(DataContainer):
            name: str
            value: str | None
            def __init__(self, *args, name: str = ..., value: str | None = ..., version: int | None = None, **kwargs) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        resource_type: int
        resource_name: str
        configs: list[AlterableConfig]
        def __init__(
            self,
            *args,
            resource_type: int = ...,
            resource_name: str = ...,
            configs: list[AlterableConfig] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    resources: list[AlterConfigsResource]
    validate_only: bool
    def __init__(
        self, *args, resources: list[AlterConfigsResource] = ..., validate_only: bool = ..., version: int | None = None, **kwargs
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

class AlterConfigsResponse(ApiMessage):
    class AlterConfigsResourceResponse(DataContainer):
        error_code: int
        error_message: str | None
        resource_type: int
        resource_name: str
        def __init__(
            self,
            *args,
            error_code: int = ...,
            error_message: str | None = ...,
            resource_type: int = ...,
            resource_name: str = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    responses: list[AlterConfigsResourceResponse]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        responses: list[AlterConfigsResourceResponse] = ...,
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

class DescribeConfigsRequest(ApiMessage):
    class DescribeConfigsResource(DataContainer):
        resource_type: int
        resource_name: str
        configuration_keys: list[str] | None
        def __init__(
            self,
            *args,
            resource_type: int = ...,
            resource_name: str = ...,
            configuration_keys: list[str] | None = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    resources: list[DescribeConfigsResource]
    include_synonyms: bool
    include_documentation: bool
    def __init__(
        self,
        *args,
        resources: list[DescribeConfigsResource] = ...,
        include_synonyms: bool = ...,
        include_documentation: bool = ...,
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

class DescribeConfigsResponse(ApiMessage):
    class DescribeConfigsResult(DataContainer):
        class DescribeConfigsResourceResult(DataContainer):
            class DescribeConfigsSynonym(DataContainer):
                name: str
                value: str | None
                source: int
                def __init__(
                    self, *args, name: str = ..., value: str | None = ..., source: int = ..., version: int | None = None, **kwargs
                ) -> None: ...
                @property
                def version(self) -> int | None: ...
                def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

            name: str
            value: str | None
            read_only: bool
            config_source: int
            is_default: bool
            is_sensitive: bool
            synonyms: list[DescribeConfigsSynonym]
            config_type: int
            documentation: str | None
            def __init__(
                self,
                *args,
                name: str = ...,
                value: str | None = ...,
                read_only: bool = ...,
                config_source: int = ...,
                is_default: bool = ...,
                is_sensitive: bool = ...,
                synonyms: list[DescribeConfigsSynonym] = ...,
                config_type: int = ...,
                documentation: str | None = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        error_code: int
        error_message: str | None
        resource_type: int
        resource_name: str
        configs: list[DescribeConfigsResourceResult]
        def __init__(
            self,
            *args,
            error_code: int = ...,
            error_message: str | None = ...,
            resource_type: int = ...,
            resource_name: str = ...,
            configs: list[DescribeConfigsResourceResult] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    results: list[DescribeConfigsResult]
    def __init__(
        self, *args, throttle_time_ms: int = ..., results: list[DescribeConfigsResult] = ..., version: int | None = None, **kwargs
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

class IncrementalAlterConfigsRequest(ApiMessage):
    class AlterConfigsResource(DataContainer):
        class AlterableConfig(DataContainer):
            name: str
            config_operation: int
            value: str | None
            def __init__(
                self,
                *args,
                name: str = ...,
                config_operation: int = ...,
                value: str | None = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        resource_type: int
        resource_name: str
        configs: list[AlterableConfig]
        def __init__(
            self,
            *args,
            resource_type: int = ...,
            resource_name: str = ...,
            configs: list[AlterableConfig] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    resources: list[AlterConfigsResource]
    validate_only: bool
    def __init__(
        self, *args, resources: list[AlterConfigsResource] = ..., validate_only: bool = ..., version: int | None = None, **kwargs
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

class IncrementalAlterConfigsResponse(ApiMessage):
    class AlterConfigsResourceResponse(DataContainer):
        error_code: int
        error_message: str | None
        resource_type: int
        resource_name: str
        def __init__(
            self,
            *args,
            error_code: int = ...,
            error_message: str | None = ...,
            resource_type: int = ...,
            resource_name: str = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    responses: list[AlterConfigsResourceResponse]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        responses: list[AlterConfigsResourceResponse] = ...,
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

class ListConfigResourcesRequest(ApiMessage):
    resource_types: list[int]
    def __init__(self, *args, resource_types: list[int] = ..., version: int | None = None, **kwargs) -> None: ...
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

class ListConfigResourcesResponse(ApiMessage):
    class ConfigResource(DataContainer):
        resource_name: str
        resource_type: int
        def __init__(
            self, *args, resource_name: str = ..., resource_type: int = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    config_resources: list[ConfigResource]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        config_resources: list[ConfigResource] = ...,
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

__all__ = [
    "AlterConfigsRequest",
    "AlterConfigsResponse",
    "DescribeConfigsRequest",
    "DescribeConfigsResponse",
    "IncrementalAlterConfigsRequest",
    "IncrementalAlterConfigsResponse",
    "ListConfigResourcesRequest",
    "ListConfigResourcesResponse",
]
