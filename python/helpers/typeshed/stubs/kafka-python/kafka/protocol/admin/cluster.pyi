import uuid
from _typeshed import Incomplete

from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class DescribeClusterRequest(ApiMessage):
    include_cluster_authorized_operations: bool
    endpoint_type: int
    include_fenced_brokers: bool
    def __init__(
        self,
        *args,
        include_cluster_authorized_operations: bool = ...,
        endpoint_type: int = ...,
        include_fenced_brokers: bool = ...,
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

class DescribeClusterResponse(ApiMessage):
    class DescribeClusterBroker(DataContainer):
        broker_id: int
        host: str
        port: int
        rack: str | None
        is_fenced: bool
        def __init__(
            self,
            *args,
            broker_id: int = ...,
            host: str = ...,
            port: int = ...,
            rack: str | None = ...,
            is_fenced: bool = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    error_message: str | None
    endpoint_type: int
    cluster_id: str
    controller_id: int
    brokers: list[DescribeClusterBroker]
    authorized_operations: set[int]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        error_message: str | None = ...,
        endpoint_type: int = ...,
        cluster_id: str = ...,
        controller_id: int = ...,
        brokers: list[DescribeClusterBroker] = ...,
        authorized_operations: set[int] = ...,
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

class DescribeLogDirsRequest(ApiMessage):
    class DescribableLogDirTopic(DataContainer):
        topic: str
        partitions: list[int]
        def __init__(
            self, *args, topic: str = ..., partitions: list[int] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    topics: list[DescribableLogDirTopic] | None
    def __init__(
        self, *args, topics: list[DescribableLogDirTopic] | None = ..., version: int | None = None, **kwargs
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

class DescribeLogDirsResponse(ApiMessage):
    class DescribeLogDirsResult(DataContainer):
        class DescribeLogDirsTopic(DataContainer):
            class DescribeLogDirsPartition(DataContainer):
                partition_index: int
                partition_size: int
                offset_lag: int
                is_future_key: bool
                def __init__(
                    self,
                    *args,
                    partition_index: int = ...,
                    partition_size: int = ...,
                    offset_lag: int = ...,
                    is_future_key: bool = ...,
                    version: int | None = None,
                    **kwargs,
                ) -> None: ...
                @property
                def version(self) -> int | None: ...
                def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

            name: str
            partitions: list[DescribeLogDirsPartition]
            def __init__(
                self,
                *args,
                name: str = ...,
                partitions: list[DescribeLogDirsPartition] = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        error_code: int
        log_dir: str
        topics: list[DescribeLogDirsTopic]
        total_bytes: int
        usable_bytes: int
        is_cordoned: bool
        def __init__(
            self,
            *args,
            error_code: int = ...,
            log_dir: str = ...,
            topics: list[DescribeLogDirsTopic] = ...,
            total_bytes: int = ...,
            usable_bytes: int = ...,
            is_cordoned: bool = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    results: list[DescribeLogDirsResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        results: list[DescribeLogDirsResult] = ...,
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

class AlterReplicaLogDirsRequest(ApiMessage):
    class AlterReplicaLogDir(DataContainer):
        class AlterReplicaLogDirTopic(DataContainer):
            name: str
            partitions: list[int]
            def __init__(
                self, *args, name: str = ..., partitions: list[int] = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        path: str
        topics: list[AlterReplicaLogDirTopic]
        def __init__(
            self, *args, path: str = ..., topics: list[AlterReplicaLogDirTopic] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    dirs: list[AlterReplicaLogDir]
    def __init__(self, *args, dirs: list[AlterReplicaLogDir] = ..., version: int | None = None, **kwargs) -> None: ...
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

class AlterReplicaLogDirsResponse(ApiMessage):
    class AlterReplicaLogDirTopicResult(DataContainer):
        class AlterReplicaLogDirPartitionResult(DataContainer):
            partition_index: int
            error_code: int
            def __init__(
                self, *args, partition_index: int = ..., error_code: int = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        topic_name: str
        partitions: list[AlterReplicaLogDirPartitionResult]
        def __init__(
            self,
            *args,
            topic_name: str = ...,
            partitions: list[AlterReplicaLogDirPartitionResult] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    results: list[AlterReplicaLogDirTopicResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        results: list[AlterReplicaLogDirTopicResult] = ...,
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

class DescribeQuorumRequest(ApiMessage):
    class TopicData(DataContainer):
        class PartitionData(DataContainer):
            partition_index: int
            def __init__(self, *args, partition_index: int = ..., version: int | None = None, **kwargs) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        topic_name: str
        partitions: list[PartitionData]
        def __init__(
            self, *args, topic_name: str = ..., partitions: list[PartitionData] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    topics: list[TopicData]
    def __init__(self, *args, topics: list[TopicData] = ..., version: int | None = None, **kwargs) -> None: ...
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

class DescribeQuorumResponse(ApiMessage):
    class TopicData(DataContainer):
        class PartitionData(DataContainer):
            class ReplicaState(DataContainer):
                replica_id: int
                replica_directory_id: uuid.UUID
                log_end_offset: int
                last_fetch_timestamp: int
                last_caught_up_timestamp: int
                def __init__(
                    self,
                    *args,
                    replica_id: int = ...,
                    replica_directory_id: uuid.UUID = ...,
                    log_end_offset: int = ...,
                    last_fetch_timestamp: int = ...,
                    last_caught_up_timestamp: int = ...,
                    version: int | None = None,
                    **kwargs,
                ) -> None: ...
                @property
                def version(self) -> int | None: ...
                def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

            partition_index: int
            error_code: int
            error_message: str | None
            leader_id: int
            leader_epoch: int
            high_watermark: int
            current_voters: list[ReplicaState]
            observers: list[ReplicaState]
            def __init__(
                self,
                *args,
                partition_index: int = ...,
                error_code: int = ...,
                error_message: str | None = ...,
                leader_id: int = ...,
                leader_epoch: int = ...,
                high_watermark: int = ...,
                current_voters: list[ReplicaState] = ...,
                observers: list[ReplicaState] = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        topic_name: str
        partitions: list[PartitionData]
        def __init__(
            self, *args, topic_name: str = ..., partitions: list[PartitionData] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class Node(DataContainer):
        class Listener(DataContainer):
            name: str
            host: str
            port: int
            def __init__(
                self, *args, name: str = ..., host: str = ..., port: int = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        node_id: int
        listeners: list[Listener]
        def __init__(
            self, *args, node_id: int = ..., listeners: list[Listener] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    error_code: int
    error_message: str | None
    topics: list[TopicData]
    nodes: list[Node]
    def __init__(
        self,
        *args,
        error_code: int = ...,
        error_message: str | None = ...,
        topics: list[TopicData] = ...,
        nodes: list[Node] = ...,
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

class UpdateFeaturesRequest(ApiMessage):
    class FeatureUpdateKey(DataContainer):
        feature: str
        max_version_level: int
        allow_downgrade: bool
        upgrade_type: int
        def __init__(
            self,
            *args,
            feature: str = ...,
            max_version_level: int = ...,
            allow_downgrade: bool = ...,
            upgrade_type: int = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    timeout_ms: int
    feature_updates: list[FeatureUpdateKey]
    validate_only: bool
    def __init__(
        self,
        *args,
        timeout_ms: int = ...,
        feature_updates: list[FeatureUpdateKey] = ...,
        validate_only: bool = ...,
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

class UpdateFeaturesResponse(ApiMessage):
    class UpdatableFeatureResult(DataContainer):
        feature: str
        error_code: int
        error_message: str | None
        def __init__(
            self,
            *args,
            feature: str = ...,
            error_code: int = ...,
            error_message: str | None = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    error_message: str | None
    results: list[UpdatableFeatureResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        error_message: str | None = ...,
        results: list[UpdatableFeatureResult] = ...,
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
    "DescribeClusterRequest",
    "DescribeClusterResponse",
    "DescribeLogDirsRequest",
    "DescribeLogDirsResponse",
    "AlterReplicaLogDirsRequest",
    "AlterReplicaLogDirsResponse",
    "DescribeQuorumRequest",
    "DescribeQuorumResponse",
    "UpdateFeaturesRequest",
    "UpdateFeaturesResponse",
]
