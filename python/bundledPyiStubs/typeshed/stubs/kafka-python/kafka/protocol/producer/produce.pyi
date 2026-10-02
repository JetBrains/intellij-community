import uuid
from _typeshed import Incomplete

from kafka.protocol.api_data import ApiData
from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class ProduceRequest(ApiMessage):
    class TopicProduceData(DataContainer):
        class PartitionProduceData(DataContainer):
            index: int
            records: bytes | ApiData | None
            def __init__(
                self, *args, index: int = ..., records: bytes | ApiData | None = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        topic_id: uuid.UUID
        partition_data: list[PartitionProduceData]
        def __init__(
            self,
            *args,
            name: str = ...,
            topic_id: uuid.UUID = ...,
            partition_data: list[PartitionProduceData] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    transactional_id: str | None
    acks: int
    timeout_ms: int
    topic_data: list[TopicProduceData]
    def __init__(
        self,
        *args,
        transactional_id: str | None = ...,
        acks: int = ...,
        timeout_ms: int = ...,
        topic_data: list[TopicProduceData] = ...,
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
    def with_header(self, correlation_id: int = 0, client_id: str = "kafka-python") -> None: ...
    def expect_response(self) -> bool: ...

class ProduceResponse(ApiMessage):
    class TopicProduceResponse(DataContainer):
        class PartitionProduceResponse(DataContainer):
            class BatchIndexAndErrorMessage(DataContainer):
                batch_index: int
                batch_index_error_message: str | None
                def __init__(
                    self,
                    *args,
                    batch_index: int = ...,
                    batch_index_error_message: str | None = ...,
                    version: int | None = None,
                    **kwargs,
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

            index: int
            error_code: int
            base_offset: int
            log_append_time_ms: int
            log_start_offset: int
            record_errors: list[BatchIndexAndErrorMessage]
            error_message: str | None
            current_leader: LeaderIdAndEpoch
            def __init__(
                self,
                *args,
                index: int = ...,
                error_code: int = ...,
                base_offset: int = ...,
                log_append_time_ms: int = ...,
                log_start_offset: int = ...,
                record_errors: list[BatchIndexAndErrorMessage] = ...,
                error_message: str | None = ...,
                current_leader: LeaderIdAndEpoch = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        topic_id: uuid.UUID
        partition_responses: list[PartitionProduceResponse]
        def __init__(
            self,
            *args,
            name: str = ...,
            topic_id: uuid.UUID = ...,
            partition_responses: list[PartitionProduceResponse] = ...,
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

    responses: list[TopicProduceResponse]
    throttle_time_ms: int
    node_endpoints: list[NodeEndpoint]
    def __init__(
        self,
        *args,
        responses: list[TopicProduceResponse] = ...,
        throttle_time_ms: int = ...,
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

__all__ = ["ProduceRequest", "ProduceResponse"]
