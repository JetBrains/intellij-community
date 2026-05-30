from _typeshed import Incomplete
from typing import ClassVar, NamedTuple

import kafka.errors as Errors

log: Incomplete
READ_UNCOMMITTED: int
READ_COMMITTED: int
ISOLATION_LEVEL_CONFIG: Incomplete

class ConsumerRecord(NamedTuple):
    topic: str
    partition: int
    leader_epoch: int | None
    offset: int
    timestamp: int
    timestamp_type: int
    key: Incomplete
    value: Incomplete
    headers: list[tuple[str, bytes]]
    checksum: int | None
    serialized_key_size: int
    serialized_value_size: int
    serialized_header_size: int

class CompletedFetch(NamedTuple):
    topic_partition: Incomplete
    fetched_offset: Incomplete
    response_version: Incomplete
    partition_data: Incomplete
    metric_aggregator: Incomplete

class ExceptionMetadata(NamedTuple):
    partition: Incomplete
    fetched_offset: Incomplete
    exception: Incomplete

class NoOffsetForPartitionError(Errors.KafkaError): ...
class RecordTooLargeError(Errors.KafkaError): ...

class Fetcher:
    DEFAULT_CONFIG: Incomplete
    config: Incomplete
    def __init__(self, client, subscriptions, **configs) -> None: ...
    def send_fetches(self): ...
    def in_flight_fetches(self): ...
    def reset_offsets_if_needed(self): ...
    def offsets_by_times(self, timestamps, timeout_ms=None): ...
    def beginning_offsets(self, partitions, timeout_ms): ...
    def end_offsets(self, partitions, timeout_ms): ...
    def beginning_or_end_offset(self, partitions, timestamp, timeout_ms): ...
    def fetched_records(self, max_records=None, update_offsets: bool = True): ...
    def close(self) -> None: ...

    class PartitionRecords:
        fetch_offset: Incomplete
        topic_partition: Incomplete
        leader_epoch: int
        next_fetch_offset: Incomplete
        bytes_read: int
        records_read: int
        isolation_level: Incomplete
        aborted_producer_ids: Incomplete
        aborted_transactions: Incomplete
        metric_aggregator: Incomplete
        check_crcs: Incomplete
        record_iterator: Incomplete
        on_drain: Incomplete
        def __init__(
            self,
            fetch_offset,
            tp,
            records,
            key_deserializer=None,
            value_deserializer=None,
            check_crcs: bool = True,
            isolation_level=0,
            aborted_transactions=None,
            metric_aggregator=None,
            on_drain=...,
        ) -> None: ...
        def __bool__(self) -> bool: ...
        __nonzero__ = __bool__
        def drain(self) -> None: ...
        def take(self, n=None): ...

class FetchSessionHandler:
    node_id: Incomplete
    next_metadata: Incomplete
    session_partitions: Incomplete
    def __init__(self, node_id) -> None: ...
    def build_next(self, next_partitions): ...
    def handle_response(self, response): ...
    def handle_error(self, _exception) -> None: ...

class FetchMetadata:
    MAX_EPOCH: int
    INVALID_SESSION_ID: int
    THROTTLED_SESSION_ID: int
    INITIAL_EPOCH: int
    FINAL_EPOCH: int
    INITIAL: ClassVar[FetchMetadata]
    LEGACY: ClassVar[FetchMetadata]
    session_id: Incomplete
    epoch: Incomplete
    def __init__(self, session_id, epoch) -> None: ...
    @property
    def is_full(self): ...
    @classmethod
    def next_epoch(cls, prev_epoch): ...
    def next_close_existing(self): ...
    @classmethod
    def new_incremental(cls, session_id): ...
    def next_incremental(self): ...

class FetchRequestData:
    def __init__(self, to_send, to_forget, metadata) -> None: ...
    @property
    def metadata(self): ...
    @property
    def id(self): ...
    @property
    def epoch(self): ...
    @property
    def to_send(self): ...
    @property
    def to_forget(self): ...

class FetchMetrics:
    total_bytes: int
    total_records: int
    def __init__(self) -> None: ...

class FetchResponseMetricAggregator:
    sensors: Incomplete
    unrecorded_partitions: Incomplete
    fetch_metrics: Incomplete
    topic_fetch_metrics: Incomplete
    def __init__(self, sensors, partitions) -> None: ...
    def record(self, partition, num_bytes, num_records) -> None: ...

class FetchManagerMetrics:
    metrics: Incomplete
    group_name: Incomplete
    bytes_fetched: Incomplete
    records_fetched: Incomplete
    fetch_latency: Incomplete
    records_fetch_lag: Incomplete
    def __init__(self, metrics, prefix) -> None: ...
    def record_topic_fetch_metrics(self, topic, num_bytes, num_records) -> None: ...
