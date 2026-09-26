import uuid
from _typeshed import Incomplete
from enum import IntEnum

from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class CreateTopicsRequest(ApiMessage):
    class CreatableTopic(DataContainer):
        class CreatableReplicaAssignment(DataContainer):
            partition_index: int
            broker_ids: list[int]
            def __init__(
                self, *args, partition_index: int = ..., broker_ids: list[int] = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        class CreatableTopicConfig(DataContainer):
            name: str
            value: str | None
            def __init__(self, *args, name: str = ..., value: str | None = ..., version: int | None = None, **kwargs) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        num_partitions: int
        replication_factor: int
        assignments: list[CreatableReplicaAssignment]
        configs: list[CreatableTopicConfig]
        def __init__(
            self,
            *args,
            name: str = ...,
            num_partitions: int = ...,
            replication_factor: int = ...,
            assignments: list[CreatableReplicaAssignment] = ...,
            configs: list[CreatableTopicConfig] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    topics: list[CreatableTopic]
    timeout_ms: int
    validate_only: bool
    def __init__(
        self,
        *args,
        topics: list[CreatableTopic] = ...,
        timeout_ms: int = ...,
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

class CreateTopicsResponse(ApiMessage):
    class CreatableTopicResult(DataContainer):
        class CreatableTopicConfigs(DataContainer):
            name: str
            value: str | None
            read_only: bool
            config_source: int
            is_sensitive: bool
            def __init__(
                self,
                *args,
                name: str = ...,
                value: str | None = ...,
                read_only: bool = ...,
                config_source: int = ...,
                is_sensitive: bool = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        topic_id: uuid.UUID
        error_code: int
        error_message: str | None
        topic_config_error_code: int
        num_partitions: int
        replication_factor: int
        configs: list[CreatableTopicConfigs] | None
        def __init__(
            self,
            *args,
            name: str = ...,
            topic_id: uuid.UUID = ...,
            error_code: int = ...,
            error_message: str | None = ...,
            topic_config_error_code: int = ...,
            num_partitions: int = ...,
            replication_factor: int = ...,
            configs: list[CreatableTopicConfigs] | None = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    topics: list[CreatableTopicResult]
    def __init__(
        self, *args, throttle_time_ms: int = ..., topics: list[CreatableTopicResult] = ..., version: int | None = None, **kwargs
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

class DeleteTopicsRequest(ApiMessage):
    class DeleteTopicState(DataContainer):
        name: str | None
        topic_id: uuid.UUID
        def __init__(
            self, *args, name: str | None = ..., topic_id: uuid.UUID = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    topics: list[DeleteTopicState]
    topic_names: list[str]
    timeout_ms: int
    def __init__(
        self,
        *args,
        topics: list[DeleteTopicState] = ...,
        topic_names: list[str] = ...,
        timeout_ms: int = ...,
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

class DeleteTopicsResponse(ApiMessage):
    class DeletableTopicResult(DataContainer):
        name: str | None
        topic_id: uuid.UUID
        error_code: int
        error_message: str | None
        def __init__(
            self,
            *args,
            name: str | None = ...,
            topic_id: uuid.UUID = ...,
            error_code: int = ...,
            error_message: str | None = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    responses: list[DeletableTopicResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        responses: list[DeletableTopicResult] = ...,
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

class CreatePartitionsRequest(ApiMessage):
    class CreatePartitionsTopic(DataContainer):
        class CreatePartitionsAssignment(DataContainer):
            broker_ids: list[int]
            def __init__(self, *args, broker_ids: list[int] = ..., version: int | None = None, **kwargs) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        count: int
        assignments: list[CreatePartitionsAssignment] | None
        def __init__(
            self,
            *args,
            name: str = ...,
            count: int = ...,
            assignments: list[CreatePartitionsAssignment] | None = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    topics: list[CreatePartitionsTopic]
    timeout_ms: int
    validate_only: bool
    def __init__(
        self,
        *args,
        topics: list[CreatePartitionsTopic] = ...,
        timeout_ms: int = ...,
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
    @classmethod
    def json_patch(cls, json): ...

class CreatePartitionsResponse(ApiMessage):
    class CreatePartitionsTopicResult(DataContainer):
        name: str
        error_code: int
        error_message: str | None
        def __init__(
            self,
            *args,
            name: str = ...,
            error_code: int = ...,
            error_message: str | None = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    results: list[CreatePartitionsTopicResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        results: list[CreatePartitionsTopicResult] = ...,
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

class AlterPartitionReassignmentsRequest(ApiMessage):
    class ReassignableTopic(DataContainer):
        class ReassignablePartition(DataContainer):
            partition_index: int
            replicas: list[int] | None
            def __init__(
                self, *args, partition_index: int = ..., replicas: list[int] | None = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        partitions: list[ReassignablePartition]
        def __init__(
            self, *args, name: str = ..., partitions: list[ReassignablePartition] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    timeout_ms: int
    allow_replication_factor_change: bool
    topics: list[ReassignableTopic]
    def __init__(
        self,
        *args,
        timeout_ms: int = ...,
        allow_replication_factor_change: bool = ...,
        topics: list[ReassignableTopic] = ...,
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

class AlterPartitionReassignmentsResponse(ApiMessage):
    class ReassignableTopicResponse(DataContainer):
        class ReassignablePartitionResponse(DataContainer):
            partition_index: int
            error_code: int
            error_message: str | None
            def __init__(
                self,
                *args,
                partition_index: int = ...,
                error_code: int = ...,
                error_message: str | None = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        partitions: list[ReassignablePartitionResponse]
        def __init__(
            self,
            *args,
            name: str = ...,
            partitions: list[ReassignablePartitionResponse] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    allow_replication_factor_change: bool
    error_code: int
    error_message: str | None
    responses: list[ReassignableTopicResponse]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        allow_replication_factor_change: bool = ...,
        error_code: int = ...,
        error_message: str | None = ...,
        responses: list[ReassignableTopicResponse] = ...,
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

class ListPartitionReassignmentsRequest(ApiMessage):
    class ListPartitionReassignmentsTopics(DataContainer):
        name: str
        partition_indexes: list[int]
        def __init__(
            self, *args, name: str = ..., partition_indexes: list[int] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    timeout_ms: int
    topics: list[ListPartitionReassignmentsTopics] | None
    def __init__(
        self,
        *args,
        timeout_ms: int = ...,
        topics: list[ListPartitionReassignmentsTopics] | None = ...,
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

class ListPartitionReassignmentsResponse(ApiMessage):
    class OngoingTopicReassignment(DataContainer):
        class OngoingPartitionReassignment(DataContainer):
            partition_index: int
            replicas: list[int]
            adding_replicas: list[int]
            removing_replicas: list[int]
            def __init__(
                self,
                *args,
                partition_index: int = ...,
                replicas: list[int] = ...,
                adding_replicas: list[int] = ...,
                removing_replicas: list[int] = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        partitions: list[OngoingPartitionReassignment]
        def __init__(
            self,
            *args,
            name: str = ...,
            partitions: list[OngoingPartitionReassignment] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    error_message: str | None
    topics: list[OngoingTopicReassignment]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        error_message: str | None = ...,
        topics: list[OngoingTopicReassignment] = ...,
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

class DescribeTopicPartitionsRequest(ApiMessage):
    class TopicRequest(DataContainer):
        name: str
        def __init__(self, *args, name: str = ..., version: int | None = None, **kwargs) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class Cursor(DataContainer):
        topic_name: str
        partition_index: int
        def __init__(
            self, *args, topic_name: str = ..., partition_index: int = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    topics: list[TopicRequest]
    response_partition_limit: int
    cursor: Cursor | None
    def __init__(
        self,
        *args,
        topics: list[TopicRequest] = ...,
        response_partition_limit: int = ...,
        cursor: Cursor | None = ...,
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

class DescribeTopicPartitionsResponse(ApiMessage):
    class DescribeTopicPartitionsResponseTopic(DataContainer):
        class DescribeTopicPartitionsResponsePartition(DataContainer):
            error_code: int
            partition_index: int
            leader_id: int
            leader_epoch: int
            replica_nodes: list[int]
            isr_nodes: list[int]
            eligible_leader_replicas: list[int] | None
            last_known_elr: list[int] | None
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
                eligible_leader_replicas: list[int] | None = ...,
                last_known_elr: list[int] | None = ...,
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
        partitions: list[DescribeTopicPartitionsResponsePartition]
        topic_authorized_operations: int
        def __init__(
            self,
            *args,
            error_code: int = ...,
            name: str | None = ...,
            topic_id: uuid.UUID = ...,
            is_internal: bool = ...,
            partitions: list[DescribeTopicPartitionsResponsePartition] = ...,
            topic_authorized_operations: int = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class Cursor(DataContainer):
        topic_name: str
        partition_index: int
        def __init__(
            self, *args, topic_name: str = ..., partition_index: int = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    topics: list[DescribeTopicPartitionsResponseTopic]
    next_cursor: Cursor | None
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        topics: list[DescribeTopicPartitionsResponseTopic] = ...,
        next_cursor: Cursor | None = ...,
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

class DeleteRecordsRequest(ApiMessage):
    class DeleteRecordsTopic(DataContainer):
        class DeleteRecordsPartition(DataContainer):
            partition_index: int
            offset: int
            def __init__(
                self, *args, partition_index: int = ..., offset: int = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        partitions: list[DeleteRecordsPartition]
        def __init__(
            self, *args, name: str = ..., partitions: list[DeleteRecordsPartition] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    topics: list[DeleteRecordsTopic]
    timeout_ms: int
    def __init__(
        self, *args, topics: list[DeleteRecordsTopic] = ..., timeout_ms: int = ..., version: int | None = None, **kwargs
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

class DeleteRecordsResponse(ApiMessage):
    class DeleteRecordsTopicResult(DataContainer):
        class DeleteRecordsPartitionResult(DataContainer):
            partition_index: int
            low_watermark: int
            error_code: int
            def __init__(
                self,
                *args,
                partition_index: int = ...,
                low_watermark: int = ...,
                error_code: int = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        partitions: list[DeleteRecordsPartitionResult]
        def __init__(
            self,
            *args,
            name: str = ...,
            partitions: list[DeleteRecordsPartitionResult] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    topics: list[DeleteRecordsTopicResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        topics: list[DeleteRecordsTopicResult] = ...,
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

class ElectLeadersRequest(ApiMessage):
    class TopicPartitions(DataContainer):
        topic: str
        partitions: list[int]
        def __init__(
            self, *args, topic: str = ..., partitions: list[int] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    election_type: int
    topic_partitions: list[TopicPartitions] | None
    timeout_ms: int
    def __init__(
        self,
        *args,
        election_type: int = ...,
        topic_partitions: list[TopicPartitions] | None = ...,
        timeout_ms: int = ...,
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

class ElectLeadersResponse(ApiMessage):
    class ReplicaElectionResult(DataContainer):
        class PartitionResult(DataContainer):
            partition_id: int
            error_code: int
            error_message: str | None
            def __init__(
                self,
                *args,
                partition_id: int = ...,
                error_code: int = ...,
                error_message: str | None = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        topic: str
        partition_result: list[PartitionResult]
        def __init__(
            self, *args, topic: str = ..., partition_result: list[PartitionResult] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    replica_election_results: list[ReplicaElectionResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        replica_election_results: list[ReplicaElectionResult] = ...,
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

class ElectionType(IntEnum):
    PREFERRED = 0
    UNCLEAN = 1

__all__ = [
    "CreateTopicsRequest",
    "CreateTopicsResponse",
    "DeleteTopicsRequest",
    "DeleteTopicsResponse",
    "CreatePartitionsRequest",
    "CreatePartitionsResponse",
    "AlterPartitionReassignmentsRequest",
    "AlterPartitionReassignmentsResponse",
    "ListPartitionReassignmentsRequest",
    "ListPartitionReassignmentsResponse",
    "DescribeTopicPartitionsRequest",
    "DescribeTopicPartitionsResponse",
    "DeleteRecordsRequest",
    "DeleteRecordsResponse",
    "ElectLeadersRequest",
    "ElectLeadersResponse",
    "ElectionType",
]
