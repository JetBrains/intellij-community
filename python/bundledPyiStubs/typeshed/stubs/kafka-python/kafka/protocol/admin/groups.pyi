from _typeshed import Incomplete

from kafka.protocol.api_data import ApiData
from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class DescribeGroupsRequest(ApiMessage):
    groups: list[str]
    include_authorized_operations: bool
    def __init__(
        self, *args, groups: list[str] = ..., include_authorized_operations: bool = ..., version: int | None = None, **kwargs
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

class DescribeGroupsResponse(ApiMessage):
    class DescribedGroup(DataContainer):
        class DescribedGroupMember(DataContainer):
            member_id: str
            group_instance_id: str | None
            client_id: str
            client_host: str
            member_metadata: bytes | ApiData
            member_assignment: bytes | ApiData
            def __init__(
                self,
                *args,
                member_id: str = ...,
                group_instance_id: str | None = ...,
                client_id: str = ...,
                client_host: str = ...,
                member_metadata: bytes | ApiData = ...,
                member_assignment: bytes | ApiData = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        error_code: int
        error_message: str | None
        group_id: str
        group_state: str
        protocol_type: str
        protocol_data: str
        members: list[DescribedGroupMember]
        authorized_operations: set[int]
        def __init__(
            self,
            *args,
            error_code: int = ...,
            error_message: str | None = ...,
            group_id: str = ...,
            group_state: str = ...,
            protocol_type: str = ...,
            protocol_data: str = ...,
            members: list[DescribedGroupMember] = ...,
            authorized_operations: set[int] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    groups: list[DescribedGroup]
    def __init__(
        self, *args, throttle_time_ms: int = ..., groups: list[DescribedGroup] = ..., version: int | None = None, **kwargs
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
    def json_patch(cls, json): ...

class ListGroupsRequest(ApiMessage):
    states_filter: list[str]
    types_filter: list[str]
    def __init__(
        self, *args, states_filter: list[str] = ..., types_filter: list[str] = ..., version: int | None = None, **kwargs
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

class ListGroupsResponse(ApiMessage):
    class ListedGroup(DataContainer):
        group_id: str
        protocol_type: str
        group_state: str
        group_type: str
        def __init__(
            self,
            *args,
            group_id: str = ...,
            protocol_type: str = ...,
            group_state: str = ...,
            group_type: str = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    groups: list[ListedGroup]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        groups: list[ListedGroup] = ...,
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

class DeleteGroupsRequest(ApiMessage):
    groups_names: list[str]
    def __init__(self, *args, groups_names: list[str] = ..., version: int | None = None, **kwargs) -> None: ...
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

class DeleteGroupsResponse(ApiMessage):
    class DeletableGroupResult(DataContainer):
        group_id: str
        error_code: int
        def __init__(self, *args, group_id: str = ..., error_code: int = ..., version: int | None = None, **kwargs) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    results: list[DeletableGroupResult]
    def __init__(
        self, *args, throttle_time_ms: int = ..., results: list[DeletableGroupResult] = ..., version: int | None = None, **kwargs
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
    "DescribeGroupsRequest",
    "DescribeGroupsResponse",
    "ListGroupsRequest",
    "ListGroupsResponse",
    "DeleteGroupsRequest",
    "DeleteGroupsResponse",
]
