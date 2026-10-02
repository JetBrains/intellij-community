from _typeshed import Incomplete
from typing import NamedTuple

from kafka.future import Future
from kafka.structs import TopicPartition

class FutureProduceResult(Future):
    __slots__ = ("topic_partition", "_latch")
    topic_partition: TopicPartition
    def __init__(self, topic_partition: TopicPartition) -> None: ...
    def success(self, value): ...
    def failure(self, error): ...
    def wait(self, timeout=None): ...

class FutureRecordMetadata(Future):
    __slots__ = ("_produce_future", "args")
    args: Incomplete
    def __init__(
        self,
        produce_future,
        batch_index,
        timestamp_ms,
        checksum,
        serialized_key_size,
        serialized_value_size,
        serialized_header_size,
    ) -> None: ...
    def rebind(self, new_produce_future, new_batch_index) -> None: ...
    def get(self, timeout=None): ...

class RecordMetadata(NamedTuple):
    topic: str
    partition: int
    topic_partition: TopicPartition
    offset: int
    timestamp: int
    checksum: int | None  # Deprecated field
    serialized_key_size: int
    serialized_value_size: int
    serialized_header_size: int
