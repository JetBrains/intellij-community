from _typeshed import Incomplete
from enum import IntEnum

from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class CreateAclsRequest(ApiMessage):
    class AclCreation(DataContainer):
        resource_type: int
        resource_name: str
        resource_pattern_type: int
        principal: str
        host: str
        operation: int
        permission_type: int
        def __init__(
            self,
            *args,
            resource_type: int = ...,
            resource_name: str = ...,
            resource_pattern_type: int = ...,
            principal: str = ...,
            host: str = ...,
            operation: int = ...,
            permission_type: int = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    creations: list[AclCreation]
    def __init__(self, *args, creations: list[AclCreation] = ..., version: int | None = None, **kwargs) -> None: ...
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

class CreateAclsResponse(ApiMessage):
    class AclCreationResult(DataContainer):
        error_code: int
        error_message: str | None
        def __init__(
            self, *args, error_code: int = ..., error_message: str | None = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    results: list[AclCreationResult]
    def __init__(
        self, *args, throttle_time_ms: int = ..., results: list[AclCreationResult] = ..., version: int | None = None, **kwargs
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

class DeleteAclsRequest(ApiMessage):
    class DeleteAclsFilter(DataContainer):
        resource_type_filter: int
        resource_name_filter: str | None
        pattern_type_filter: int
        principal_filter: str | None
        host_filter: str | None
        operation: int
        permission_type: int
        def __init__(
            self,
            *args,
            resource_type_filter: int = ...,
            resource_name_filter: str | None = ...,
            pattern_type_filter: int = ...,
            principal_filter: str | None = ...,
            host_filter: str | None = ...,
            operation: int = ...,
            permission_type: int = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    filters: list[DeleteAclsFilter]
    def __init__(self, *args, filters: list[DeleteAclsFilter] = ..., version: int | None = None, **kwargs) -> None: ...
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

class DeleteAclsResponse(ApiMessage):
    class DeleteAclsFilterResult(DataContainer):
        class DeleteAclsMatchingAcl(DataContainer):
            error_code: int
            error_message: str | None
            resource_type: int
            resource_name: str
            pattern_type: int
            principal: str
            host: str
            operation: int
            permission_type: int
            def __init__(
                self,
                *args,
                error_code: int = ...,
                error_message: str | None = ...,
                resource_type: int = ...,
                resource_name: str = ...,
                pattern_type: int = ...,
                principal: str = ...,
                host: str = ...,
                operation: int = ...,
                permission_type: int = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        error_code: int
        error_message: str | None
        matching_acls: list[DeleteAclsMatchingAcl]
        def __init__(
            self,
            *args,
            error_code: int = ...,
            error_message: str | None = ...,
            matching_acls: list[DeleteAclsMatchingAcl] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    filter_results: list[DeleteAclsFilterResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        filter_results: list[DeleteAclsFilterResult] = ...,
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

class DescribeAclsRequest(ApiMessage):
    resource_type_filter: int
    resource_name_filter: str | None
    pattern_type_filter: int
    principal_filter: str | None
    host_filter: str | None
    operation: int
    permission_type: int
    def __init__(
        self,
        *args,
        resource_type_filter: int = ...,
        resource_name_filter: str | None = ...,
        pattern_type_filter: int = ...,
        principal_filter: str | None = ...,
        host_filter: str | None = ...,
        operation: int = ...,
        permission_type: int = ...,
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

class DescribeAclsResponse(ApiMessage):
    class DescribeAclsResource(DataContainer):
        class AclDescription(DataContainer):
            principal: str
            host: str
            operation: int
            permission_type: int
            def __init__(
                self,
                *args,
                principal: str = ...,
                host: str = ...,
                operation: int = ...,
                permission_type: int = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        resource_type: int
        resource_name: str
        pattern_type: int
        acls: list[AclDescription]
        def __init__(
            self,
            *args,
            resource_type: int = ...,
            resource_name: str = ...,
            pattern_type: int = ...,
            acls: list[AclDescription] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    error_message: str | None
    resources: list[DescribeAclsResource]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        error_message: str | None = ...,
        resources: list[DescribeAclsResource] = ...,
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

class ACLResourceType(IntEnum):
    UNKNOWN = 0
    ANY = 1
    TOPIC = 2
    GROUP = 3
    CLUSTER = 4
    TRANSACTIONAL_ID = 5
    DELEGATION_TOKEN = 6

class ACLOperation(IntEnum):
    UNKNOWN = 0
    ANY = 1
    ALL = 2
    READ = 3
    WRITE = 4
    CREATE = 5
    DELETE = 6
    ALTER = 7
    DESCRIBE = 8
    CLUSTER_ACTION = 9
    DESCRIBE_CONFIGS = 10
    ALTER_CONFIGS = 11
    IDEMPOTENT_WRITE = 12
    CREATE_TOKENS = 13
    DESCRIBE_TOKENS = 14

class ACLPermissionType(IntEnum):
    UNKNOWN = 0
    ANY = 1
    DENY = 2
    ALLOW = 3

class ACLResourcePatternType(IntEnum):
    UNKNOWN = 0
    ANY = 1
    MATCH = 2
    LITERAL = 3
    PREFIXED = 4

__all__ = [
    "CreateAclsRequest",
    "CreateAclsResponse",
    "DeleteAclsRequest",
    "DeleteAclsResponse",
    "DescribeAclsRequest",
    "DescribeAclsResponse",
    "ACLResourceType",
    "ACLOperation",
    "ACLPermissionType",
    "ACLResourcePatternType",
]
