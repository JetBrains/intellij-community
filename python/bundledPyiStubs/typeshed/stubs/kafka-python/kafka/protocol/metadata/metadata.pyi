import uuid
from _typeshed import Incomplete

from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class MetadataRequest(ApiMessage):
    ALL_TOPICS: Incomplete | None
    NO_TOPICS: list[Incomplete]

    class MetadataRequestTopic(DataContainer):
        topic_id: uuid.UUID
        name: str | None
        def __init__(
            self, *args, topic_id: uuid.UUID = ..., name: str | None = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    topics: list[MetadataRequestTopic] | None
    allow_auto_topic_creation: bool
    include_cluster_authorized_operations: bool
    include_topic_authorized_operations: bool
    def __init__(
        self,
        *args,
        topics: list[MetadataRequestTopic] | None = ...,
        allow_auto_topic_creation: bool = ...,
        include_cluster_authorized_operations: bool = ...,
        include_topic_authorized_operations: bool = ...,
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
    def encode(self, version=None, header=False, framed=False): ...

class MetadataResponse(ApiMessage):
    class MetadataResponseBroker(DataContainer):
        node_id: int
        host: str
        port: int
        rack: str | None
        def __init__(
            self,
            *args,
            node_id: int = ...,
            host: str = ...,
            port: int = ...,
            rack: str | None = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class MetadataResponseTopic(DataContainer):
        class MetadataResponsePartition(DataContainer):
            error_code: int
            partition_index: int
            leader_id: int
            leader_epoch: int
            replica_nodes: list[int]
            isr_nodes: list[int]
            offline_replicas: list[int]
            def __init__(
                self,
                *args,
                error_code: int = ...,
                partition_index: int = ...,
                leader_id: int = ...,
                leader_epoch: int = ...,
                replica_nodes: list[int] = ...,
                isr_nodes: list[int] = ...,
                offline_replicas: list[int] = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        error_code: int
        name: str | None
        topic_id: uuid.UUID
        is_internal: bool
        partitions: list[MetadataResponsePartition]
        authorized_operations: set[int]
        def __init__(
            self,
            *args,
            error_code: int = ...,
            name: str | None = ...,
            topic_id: uuid.UUID = ...,
            is_internal: bool = ...,
            partitions: list[MetadataResponsePartition] = ...,
            authorized_operations: set[int] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    brokers: list[MetadataResponseBroker]
    cluster_id: str | None
    controller_id: int
    topics: list[MetadataResponseTopic]
    authorized_operations: set[int]
    error_code: int
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        brokers: list[MetadataResponseBroker] = ...,
        cluster_id: str | None = ...,
        controller_id: int = ...,
        topics: list[MetadataResponseTopic] = ...,
        authorized_operations: set[int] = ...,
        error_code: int = ...,
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
    def json_patch(cls, json): ...

__all__ = ["MetadataRequest", "MetadataResponse"]
