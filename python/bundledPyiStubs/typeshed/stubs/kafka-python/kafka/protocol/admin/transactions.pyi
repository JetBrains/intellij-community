from _typeshed import Incomplete

from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class ListTransactionsRequest(ApiMessage):
    state_filters: list[str]
    producer_id_filters: list[int]
    duration_filter: int
    transactional_id_pattern: str | None
    def __init__(
        self,
        *args,
        state_filters: list[str] = ...,
        producer_id_filters: list[int] = ...,
        duration_filter: int = ...,
        transactional_id_pattern: str | None = ...,
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

class ListTransactionsResponse(ApiMessage):
    class TransactionState(DataContainer):
        transactional_id: str
        producer_id: int
        transaction_state: str
        def __init__(
            self,
            *args,
            transactional_id: str = ...,
            producer_id: int = ...,
            transaction_state: str = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    unknown_state_filters: list[str]
    transaction_states: list[TransactionState]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        unknown_state_filters: list[str] = ...,
        transaction_states: list[TransactionState] = ...,
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

class DescribeTransactionsRequest(ApiMessage):
    transactional_ids: list[str]
    def __init__(self, *args, transactional_ids: list[str] = ..., version: int | None = None, **kwargs) -> None: ...
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

class DescribeTransactionsResponse(ApiMessage):
    class TransactionState(DataContainer):
        class TopicData(DataContainer):
            topic: str
            partitions: list[int]
            def __init__(
                self, *args, topic: str = ..., partitions: list[int] = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        error_code: int
        transactional_id: str
        transaction_state: str
        transaction_timeout_ms: int
        transaction_start_time_ms: int
        producer_id: int
        producer_epoch: int
        topics: list[TopicData]
        def __init__(
            self,
            *args,
            error_code: int = ...,
            transactional_id: str = ...,
            transaction_state: str = ...,
            transaction_timeout_ms: int = ...,
            transaction_start_time_ms: int = ...,
            producer_id: int = ...,
            producer_epoch: int = ...,
            topics: list[TopicData] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    transaction_states: list[TransactionState]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        transaction_states: list[TransactionState] = ...,
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

class DescribeProducersRequest(ApiMessage):
    class TopicRequest(DataContainer):
        name: str
        partition_indexes: list[int]
        def __init__(
            self, *args, name: str = ..., partition_indexes: list[int] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    topics: list[TopicRequest]
    def __init__(self, *args, topics: list[TopicRequest] = ..., version: int | None = None, **kwargs) -> None: ...
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

class DescribeProducersResponse(ApiMessage):
    class TopicResponse(DataContainer):
        class PartitionResponse(DataContainer):
            class ProducerState(DataContainer):
                producer_id: int
                producer_epoch: int
                last_sequence: int
                last_timestamp: int
                coordinator_epoch: int
                current_txn_start_offset: int
                def __init__(
                    self,
                    *args,
                    producer_id: int = ...,
                    producer_epoch: int = ...,
                    last_sequence: int = ...,
                    last_timestamp: int = ...,
                    coordinator_epoch: int = ...,
                    current_txn_start_offset: int = ...,
                    version: int | None = None,
                    **kwargs,
                ) -> None: ...
                @property
                def version(self) -> int | None: ...
                def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

            partition_index: int
            error_code: int
            error_message: str | None
            active_producers: list[ProducerState]
            def __init__(
                self,
                *args,
                partition_index: int = ...,
                error_code: int = ...,
                error_message: str | None = ...,
                active_producers: list[ProducerState] = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        partitions: list[PartitionResponse]
        def __init__(
            self, *args, name: str = ..., partitions: list[PartitionResponse] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    topics: list[TopicResponse]
    def __init__(
        self, *args, throttle_time_ms: int = ..., topics: list[TopicResponse] = ..., version: int | None = None, **kwargs
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
    "ListTransactionsRequest",
    "ListTransactionsResponse",
    "DescribeTransactionsRequest",
    "DescribeTransactionsResponse",
    "DescribeProducersRequest",
    "DescribeProducersResponse",
]
