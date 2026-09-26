from _typeshed import Incomplete
from enum import IntEnum
from typing import Final

from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer
from kafka.util import EnumHelper

UNKNOWN_OFFSET: Final = -1

class OffsetResetStrategy:
    LATEST: Final = -1
    EARLIEST: Final = -2
    NONE: Final = 0

class IsolationLevel(EnumHelper, IntEnum):
    READ_UNCOMMITTED = 0
    READ_COMMITTED = 1

class OffsetSpec(EnumHelper, IntEnum):
    LATEST = -1
    EARLIEST = -2
    MAX_TIMESTAMP = -3
    EARLIEST_LOCAL = -4
    LATEST_TIERED = -5

class OffsetTimestamp(int):
    __slots__ = ()

class ListOffsetsRequest(ApiMessage):
    class ListOffsetsTopic(DataContainer):
        class ListOffsetsPartition(DataContainer):
            partition_index: int
            current_leader_epoch: int
            timestamp: int
            max_num_offsets: int
            def __init__(
                self,
                *args,
                partition_index: int = ...,
                current_leader_epoch: int = ...,
                timestamp: int = ...,
                max_num_offsets: int = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        partitions: list[ListOffsetsPartition]
        def __init__(
            self, *args, name: str = ..., partitions: list[ListOffsetsPartition] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    replica_id: int
    isolation_level: int
    topics: list[ListOffsetsTopic]
    timeout_ms: int
    def __init__(
        self,
        *args,
        replica_id: int = ...,
        isolation_level: int = ...,
        topics: list[ListOffsetsTopic] = ...,
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
    @classmethod
    def min_version_for_timestamp(cls, ts): ...
    @classmethod
    def min_version_for_isolation_level(cls, il): ...

class ListOffsetsResponse(ApiMessage):
    class ListOffsetsTopicResponse(DataContainer):
        class ListOffsetsPartitionResponse(DataContainer):
            partition_index: int
            error_code: int
            old_style_offsets: list[int]
            timestamp: int
            offset: int
            leader_epoch: int
            def __init__(
                self,
                *args,
                partition_index: int = ...,
                error_code: int = ...,
                old_style_offsets: list[int] = ...,
                timestamp: int = ...,
                offset: int = ...,
                leader_epoch: int = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        partitions: list[ListOffsetsPartitionResponse]
        def __init__(
            self,
            *args,
            name: str = ...,
            partitions: list[ListOffsetsPartitionResponse] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    topics: list[ListOffsetsTopicResponse]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        topics: list[ListOffsetsTopicResponse] = ...,
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

class OffsetForLeaderEpochRequest(ApiMessage):
    class OffsetForLeaderTopic(DataContainer):
        class OffsetForLeaderPartition(DataContainer):
            partition: int
            current_leader_epoch: int
            leader_epoch: int
            def __init__(
                self,
                *args,
                partition: int = ...,
                current_leader_epoch: int = ...,
                leader_epoch: int = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        topic: str
        partitions: list[OffsetForLeaderPartition]
        def __init__(
            self, *args, topic: str = ..., partitions: list[OffsetForLeaderPartition] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    replica_id: int
    topics: list[OffsetForLeaderTopic]
    def __init__(
        self, *args, replica_id: int = ..., topics: list[OffsetForLeaderTopic] = ..., version: int | None = None, **kwargs
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

class OffsetForLeaderEpochResponse(ApiMessage):
    class OffsetForLeaderTopicResult(DataContainer):
        class EpochEndOffset(DataContainer):
            error_code: int
            partition: int
            leader_epoch: int
            end_offset: int
            def __init__(
                self,
                *args,
                error_code: int = ...,
                partition: int = ...,
                leader_epoch: int = ...,
                end_offset: int = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        topic: str
        partitions: list[EpochEndOffset]
        def __init__(
            self, *args, topic: str = ..., partitions: list[EpochEndOffset] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    topics: list[OffsetForLeaderTopicResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        topics: list[OffsetForLeaderTopicResult] = ...,
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
    "UNKNOWN_OFFSET",
    "OffsetResetStrategy",
    "IsolationLevel",
    "OffsetSpec",
    "OffsetTimestamp",
    "ListOffsetsRequest",
    "ListOffsetsResponse",
    "OffsetForLeaderEpochRequest",
    "OffsetForLeaderEpochResponse",
]
