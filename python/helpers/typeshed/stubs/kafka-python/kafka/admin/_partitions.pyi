from _typeshed import Incomplete
from collections.abc import Mapping, Sequence
from typing_extensions import deprecated

from kafka.protocol.consumer import IsolationLevel, OffsetSpec as OffsetSpec, OffsetTimestamp as OffsetTimestamp
from kafka.structs import TopicPartition

class PartitionAdminMixin:
    config: dict[Incomplete, Incomplete]
    def create_partitions(
        self,
        topic_partitions: Mapping[str, int | dict[Incomplete, Incomplete] | NewPartitions],
        timeout_ms: int | None = None,
        validate_only: bool = False,
        raise_errors: bool = True,
    ): ...
    def delete_records(
        self, records_to_delete: Mapping[TopicPartition, int], timeout_ms: float | None = None, partition_leader_id=None
    ) -> dict[TopicPartition, Incomplete]: ...
    def elect_leaders(self, election_type, topic_partitions=None, timeout_ms=None, raise_errors: bool = True): ...
    def alter_partition_reassignments(self, reassignments, timeout_ms=None): ...
    def list_partition_reassignments(self, topic_partitions=None, timeout_ms=None): ...
    def describe_topic_partitions(self, topics, response_partition_limit: int = 2000, cursor=None): ...
    def list_partition_offsets(
        self, topic_partition_specs, isolation_level: IsolationLevel = IsolationLevel.READ_UNCOMMITTED, timeout_ms=None
    ): ...

@deprecated("Deprecated since v3.0.0. Use simple `dict` instead.")
class NewPartitions:
    total_count: int
    new_assignments: Sequence[Sequence[int]] | None
    def __init__(self, total_count: int, new_assignments: Sequence[Sequence[int]] | None = None) -> None: ...
