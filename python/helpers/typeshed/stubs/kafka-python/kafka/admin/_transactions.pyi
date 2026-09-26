import sys
from _typeshed import Incomplete
from typing import NamedTuple

from kafka.structs import TopicPartition
from kafka.util import EnumHelper

if sys.version_info >= (3, 11):
    from enum import StrEnum

    class TransactionState(EnumHelper, StrEnum):
        EMPTY = "Empty"
        ONGOING = "Ongoing"
        PREPARE_COMMIT = "PrepareCommit"
        PREPARE_ABORT = "PrepareAbort"
        COMPLETE_COMMIT = "CompleteCommit"
        COMPLETE_ABORT = "CompleteAbort"
        DEAD = "Dead"
        PREPARE_EPOCH_FENCE = "PrepareEpochFence"
        UNKNOWN = "Unknown"

else:
    from enum import Enum

    class TransactionState(EnumHelper, str, Enum):
        EMPTY = "Empty"
        ONGOING = "Ongoing"
        PREPARE_COMMIT = "PrepareCommit"
        PREPARE_ABORT = "PrepareAbort"
        COMPLETE_COMMIT = "CompleteCommit"
        COMPLETE_ABORT = "CompleteAbort"
        DEAD = "Dead"
        PREPARE_EPOCH_FENCE = "PrepareEpochFence"
        UNKNOWN = "Unknown"

class TransactionListing(NamedTuple):
    transactional_id: str
    producer_id: int
    state: TransactionState

class TransactionDescription(NamedTuple):
    coordinator_id: int
    state: TransactionState
    producer_id: int
    producer_epoch: int
    transaction_timeout_ms: int
    transaction_start_time_ms: int
    topic_partitions: set[TopicPartition]

class ProducerState(NamedTuple):
    producer_id: int
    producer_epoch: int
    last_sequence: int
    last_timestamp: int
    coordinator_epoch: int
    current_transaction_start_offset: int

class PartitionProducerState(NamedTuple):
    active_producers: list[ProducerState]

class AbortTransactionSpec(NamedTuple):
    topic_partition: TopicPartition
    producer_id: int
    producer_epoch: int
    coordinator_epoch: int = -1

class TransactionsAdminMixin:
    config: dict[Incomplete, Incomplete]
    def list_transactions(
        self,
        broker_ids=None,
        producer_id_filters=None,
        state_filters=None,
        duration_filter_ms: float | None = None,
        transactional_id_pattern: str | None = None,
    ): ...
    def describe_transactions(self, transactional_ids): ...
    def describe_producers(self, partitions, broker_id: int | None = None): ...
    def abort_transaction(self, spec): ...
    def find_hanging_transactions(self, broker_ids=None, max_transaction_timeout_ms: float = 900000): ...
