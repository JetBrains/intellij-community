from _typeshed import Incomplete

from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class InitProducerIdRequest(ApiMessage):
    transactional_id: str | None
    transaction_timeout_ms: int
    producer_id: int
    producer_epoch: int
    enable2_pc: bool
    keep_prepared_txn: bool
    def __init__(
        self,
        *args,
        transactional_id: str | None = ...,
        transaction_timeout_ms: int = ...,
        producer_id: int = ...,
        producer_epoch: int = ...,
        enable2_pc: bool = ...,
        keep_prepared_txn: bool = ...,
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

class InitProducerIdResponse(ApiMessage):
    throttle_time_ms: int
    error_code: int
    producer_id: int
    producer_epoch: int
    ongoing_txn_producer_id: int
    ongoing_txn_producer_epoch: int
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        producer_id: int = ...,
        producer_epoch: int = ...,
        ongoing_txn_producer_id: int = ...,
        ongoing_txn_producer_epoch: int = ...,
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

class AddPartitionsToTxnRequest(ApiMessage):
    class AddPartitionsToTxnTransaction(DataContainer):
        class AddPartitionsToTxnTopic(DataContainer):
            name: str
            partitions: list[int]
            def __init__(
                self, *args, name: str = ..., partitions: list[int] = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        transactional_id: str
        producer_id: int
        producer_epoch: int
        verify_only: bool
        topics: list[AddPartitionsToTxnTopic]
        def __init__(
            self,
            *args,
            transactional_id: str = ...,
            producer_id: int = ...,
            producer_epoch: int = ...,
            verify_only: bool = ...,
            topics: list[AddPartitionsToTxnTopic] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class AddPartitionsToTxnTopic(DataContainer):
        name: str
        partitions: list[int]
        def __init__(self, *args, name: str = ..., partitions: list[int] = ..., version: int | None = None, **kwargs) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    transactions: list[AddPartitionsToTxnTransaction]
    v3_and_below_transactional_id: str
    v3_and_below_producer_id: int
    v3_and_below_producer_epoch: int
    v3_and_below_topics: list[AddPartitionsToTxnTopic]
    def __init__(
        self,
        *args,
        transactions: list[AddPartitionsToTxnTransaction] = ...,
        v3_and_below_transactional_id: str = ...,
        v3_and_below_producer_id: int = ...,
        v3_and_below_producer_epoch: int = ...,
        v3_and_below_topics: list[AddPartitionsToTxnTopic] = ...,
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

class AddPartitionsToTxnResponse(ApiMessage):
    class AddPartitionsToTxnResult(DataContainer):
        class AddPartitionsToTxnTopicResult(DataContainer):
            class AddPartitionsToTxnPartitionResult(DataContainer):
                partition_index: int
                partition_error_code: int
                def __init__(
                    self, *args, partition_index: int = ..., partition_error_code: int = ..., version: int | None = None, **kwargs
                ) -> None: ...
                @property
                def version(self) -> int | None: ...
                def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

            name: str
            results_by_partition: list[AddPartitionsToTxnPartitionResult]
            def __init__(
                self,
                *args,
                name: str = ...,
                results_by_partition: list[AddPartitionsToTxnPartitionResult] = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        transactional_id: str
        topic_results: list[AddPartitionsToTxnTopicResult]
        def __init__(
            self,
            *args,
            transactional_id: str = ...,
            topic_results: list[AddPartitionsToTxnTopicResult] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class AddPartitionsToTxnTopicResult(DataContainer):
        class AddPartitionsToTxnPartitionResult(DataContainer):
            partition_index: int
            partition_error_code: int
            def __init__(
                self, *args, partition_index: int = ..., partition_error_code: int = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        results_by_partition: list[AddPartitionsToTxnPartitionResult]
        def __init__(
            self,
            *args,
            name: str = ...,
            results_by_partition: list[AddPartitionsToTxnPartitionResult] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    results_by_transaction: list[AddPartitionsToTxnResult]
    results_by_topic_v3_and_below: list[AddPartitionsToTxnTopicResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        results_by_transaction: list[AddPartitionsToTxnResult] = ...,
        results_by_topic_v3_and_below: list[AddPartitionsToTxnTopicResult] = ...,
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

class AddOffsetsToTxnRequest(ApiMessage):
    transactional_id: str
    producer_id: int
    producer_epoch: int
    group_id: str
    def __init__(
        self,
        *args,
        transactional_id: str = ...,
        producer_id: int = ...,
        producer_epoch: int = ...,
        group_id: str = ...,
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

class AddOffsetsToTxnResponse(ApiMessage):
    throttle_time_ms: int
    error_code: int
    def __init__(
        self, *args, throttle_time_ms: int = ..., error_code: int = ..., version: int | None = None, **kwargs
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

class EndTxnRequest(ApiMessage):
    transactional_id: str
    producer_id: int
    producer_epoch: int
    committed: bool
    def __init__(
        self,
        *args,
        transactional_id: str = ...,
        producer_id: int = ...,
        producer_epoch: int = ...,
        committed: bool = ...,
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

class EndTxnResponse(ApiMessage):
    throttle_time_ms: int
    error_code: int
    producer_id: int
    producer_epoch: int
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        producer_id: int = ...,
        producer_epoch: int = ...,
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

class TxnOffsetCommitRequest(ApiMessage):
    class TxnOffsetCommitRequestTopic(DataContainer):
        class TxnOffsetCommitRequestPartition(DataContainer):
            partition_index: int
            committed_offset: int
            committed_leader_epoch: int
            committed_metadata: str | None
            def __init__(
                self,
                *args,
                partition_index: int = ...,
                committed_offset: int = ...,
                committed_leader_epoch: int = ...,
                committed_metadata: str | None = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        partitions: list[TxnOffsetCommitRequestPartition]
        def __init__(
            self,
            *args,
            name: str = ...,
            partitions: list[TxnOffsetCommitRequestPartition] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    transactional_id: str
    group_id: str
    producer_id: int
    producer_epoch: int
    generation_id: int
    member_id: str
    group_instance_id: str | None
    topics: list[TxnOffsetCommitRequestTopic]
    def __init__(
        self,
        *args,
        transactional_id: str = ...,
        group_id: str = ...,
        producer_id: int = ...,
        producer_epoch: int = ...,
        generation_id: int = ...,
        member_id: str = ...,
        group_instance_id: str | None = ...,
        topics: list[TxnOffsetCommitRequestTopic] = ...,
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

class TxnOffsetCommitResponse(ApiMessage):
    class TxnOffsetCommitResponseTopic(DataContainer):
        class TxnOffsetCommitResponsePartition(DataContainer):
            partition_index: int
            error_code: int
            def __init__(
                self, *args, partition_index: int = ..., error_code: int = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        name: str
        partitions: list[TxnOffsetCommitResponsePartition]
        def __init__(
            self,
            *args,
            name: str = ...,
            partitions: list[TxnOffsetCommitResponsePartition] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    topics: list[TxnOffsetCommitResponseTopic]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        topics: list[TxnOffsetCommitResponseTopic] = ...,
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

class WriteTxnMarkersRequest(ApiMessage):
    class WritableTxnMarker(DataContainer):
        class WritableTxnMarkerTopic(DataContainer):
            name: str
            partition_indexes: list[int]
            def __init__(
                self, *args, name: str = ..., partition_indexes: list[int] = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        producer_id: int
        producer_epoch: int
        transaction_result: bool
        topics: list[WritableTxnMarkerTopic]
        coordinator_epoch: int
        transaction_version: int
        def __init__(
            self,
            *args,
            producer_id: int = ...,
            producer_epoch: int = ...,
            transaction_result: bool = ...,
            topics: list[WritableTxnMarkerTopic] = ...,
            coordinator_epoch: int = ...,
            transaction_version: int = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    markers: list[WritableTxnMarker]
    def __init__(self, *args, markers: list[WritableTxnMarker] = ..., version: int | None = None, **kwargs) -> None: ...
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

class WriteTxnMarkersResponse(ApiMessage):
    class WritableTxnMarkerResult(DataContainer):
        class WritableTxnMarkerTopicResult(DataContainer):
            class WritableTxnMarkerPartitionResult(DataContainer):
                partition_index: int
                error_code: int
                def __init__(
                    self, *args, partition_index: int = ..., error_code: int = ..., version: int | None = None, **kwargs
                ) -> None: ...
                @property
                def version(self) -> int | None: ...
                def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

            name: str
            partitions: list[WritableTxnMarkerPartitionResult]
            def __init__(
                self,
                *args,
                name: str = ...,
                partitions: list[WritableTxnMarkerPartitionResult] = ...,
                version: int | None = None,
                **kwargs,
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        producer_id: int
        topics: list[WritableTxnMarkerTopicResult]
        def __init__(
            self,
            *args,
            producer_id: int = ...,
            topics: list[WritableTxnMarkerTopicResult] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    markers: list[WritableTxnMarkerResult]
    def __init__(self, *args, markers: list[WritableTxnMarkerResult] = ..., version: int | None = None, **kwargs) -> None: ...
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
    "InitProducerIdRequest",
    "InitProducerIdResponse",
    "AddPartitionsToTxnRequest",
    "AddPartitionsToTxnResponse",
    "AddOffsetsToTxnRequest",
    "AddOffsetsToTxnResponse",
    "EndTxnRequest",
    "EndTxnResponse",
    "TxnOffsetCommitRequest",
    "TxnOffsetCommitResponse",
    "WriteTxnMarkersRequest",
    "WriteTxnMarkersResponse",
]
