import uuid
from _typeshed import Incomplete

from kafka.protocol.api_data import ApiData
from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class FetchRequest(ApiMessage):
    class ReplicaState(DataContainer):
        replica_id: int
        replica_epoch: int
        def __init__(
            self, *args, replica_id: int = ..., replica_epoch: int = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class FetchTopic(DataContainer):
        class FetchPartition(DataContainer):
            partition: int
            current_leader_epoch: int
            fetch_offset: int
            last_fetched_epoch: int
            log_start_offset: int
            partition_max_bytes: int
            replica_directory_id: uuid.UUID
            high_watermark: int
            def __init__(
                self,
                *args,
                partition: int = ...,
                current_leader_epoch: int = ...,
                fetch_offset: int = ...,
                last_fetched_epoch: int = ...,
                log_start_offset: int = ...,
                partition_max_bytes: int = ...,
                replica_directory_id: uuid.UUID = ...,
                high_watermark: int = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        topic: str
        topic_id: uuid.UUID
        partitions: list[FetchPartition]
        def __init__(
            self,
            *args,
            topic: str = ...,
            topic_id: uuid.UUID = ...,
            partitions: list[FetchPartition] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class ForgottenTopic(DataContainer):
        topic: str
        topic_id: uuid.UUID
        partitions: list[int]
        def __init__(
            self,
            *args,
            topic: str = ...,
            topic_id: uuid.UUID = ...,
            partitions: list[int] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    cluster_id: str | None
    replica_id: int
    replica_state: ReplicaState
    max_wait_ms: int
    min_bytes: int
    max_bytes: int
    isolation_level: int
    session_id: int
    session_epoch: int
    topics: list[FetchTopic]
    forgotten_topics_data: list[ForgottenTopic]
    rack_id: str
    def __init__(
        self,
        *args,
        cluster_id: str | None = ...,
        replica_id: int = ...,
        replica_state: ReplicaState = ...,
        max_wait_ms: int = ...,
        min_bytes: int = ...,
        max_bytes: int = ...,
        isolation_level: int = ...,
        session_id: int = ...,
        session_epoch: int = ...,
        topics: list[FetchTopic] = ...,
        forgotten_topics_data: list[ForgottenTopic] = ...,
        rack_id: str = ...,
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
    def min_version_for_isolation_level(cls, il): ...

class FetchResponse(ApiMessage):
    class FetchableTopicResponse(DataContainer):
        class PartitionData(DataContainer):
            class EpochEndOffset(DataContainer):
                epoch: int
                end_offset: int
                def __init__(
                    self, *args, epoch: int = ..., end_offset: int = ..., version: int | None = None, **kwargs
                ) -> None: ...
                @property
                def version(self) -> int | None: ...
                def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

            class LeaderIdAndEpoch(DataContainer):
                leader_id: int
                leader_epoch: int
                def __init__(
                    self, *args, leader_id: int = ..., leader_epoch: int = ..., version: int | None = None, **kwargs
                ) -> None: ...
                @property
                def version(self) -> int | None: ...
                def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

            class SnapshotId(DataContainer):
                end_offset: int
                epoch: int
                def __init__(
                    self, *args, end_offset: int = ..., epoch: int = ..., version: int | None = None, **kwargs
                ) -> None: ...
                @property
                def version(self) -> int | None: ...
                def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

            class AbortedTransaction(DataContainer):
                producer_id: int
                first_offset: int
                def __init__(
                    self, *args, producer_id: int = ..., first_offset: int = ..., version: int | None = None, **kwargs
                ) -> None: ...
                @property
                def version(self) -> int | None: ...
                def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

            partition_index: int
            error_code: int
            high_watermark: int
            last_stable_offset: int
            log_start_offset: int
            diverging_epoch: EpochEndOffset
            current_leader: LeaderIdAndEpoch
            snapshot_id: SnapshotId
            aborted_transactions: list[AbortedTransaction] | None
            preferred_read_replica: int
            records: bytes | ApiData | None
            def __init__(
                self,
                *args,
                partition_index: int = ...,
                error_code: int = ...,
                high_watermark: int = ...,
                last_stable_offset: int = ...,
                log_start_offset: int = ...,
                diverging_epoch: EpochEndOffset = ...,
                current_leader: LeaderIdAndEpoch = ...,
                snapshot_id: SnapshotId = ...,
                aborted_transactions: list[AbortedTransaction] | None = ...,
                preferred_read_replica: int = ...,
                records: bytes | ApiData | None = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        topic: str
        topic_id: uuid.UUID
        partitions: list[PartitionData]
        def __init__(
            self,
            *args,
            topic: str = ...,
            topic_id: uuid.UUID = ...,
            partitions: list[PartitionData] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class NodeEndpoint(DataContainer):
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

    throttle_time_ms: int
    error_code: int
    session_id: int
    responses: list[FetchableTopicResponse]
    node_endpoints: list[NodeEndpoint]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        session_id: int = ...,
        responses: list[FetchableTopicResponse] = ...,
        node_endpoints: list[NodeEndpoint] = ...,
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

__all__ = ["FetchRequest", "FetchResponse"]
