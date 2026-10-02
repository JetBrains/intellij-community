from typing import Final, Literal, NamedTuple

class TopicPartition(NamedTuple):
    topic: str
    partition: int

class TopicPartitionReplica(NamedTuple):
    topic: str
    partition: int
    broker_id: int

class OffsetAndMetadata(NamedTuple):
    offset: int | None = None
    metadata: str = ""
    leader_epoch: int = -1

class OffsetAndTimestamp(NamedTuple):
    offset: int
    timestamp: int
    leader_epoch: int

class MemberState:
    UNJOINED: Final = "<unjoined>"
    REBALANCING: Final = "<rebalancing>"
    STABLE: Final = "<stable>"

class ConsumerGroupMetadata(NamedTuple):
    group_id: str | None = None
    generation_id: int = -1
    member_id: str = ""
    group_instance_id: str | None = None
    state: Literal["<unjoined>", "<rebalancing>", "<stable>"] = ...  # Please, keep in sync with MemberState
