from _typeshed import Incomplete
from typing import NamedTuple

class TopicPartition(NamedTuple):
    topic: str
    partition: int

class BrokerMetadata(NamedTuple):
    nodeId: int
    host: str
    port: int
    rack: str | None

class PartitionMetadata(NamedTuple):
    topic: str
    partition: int
    leader: int
    leader_epoch: int | None
    replicas: list[int]
    isr: list[int]
    offline_replicas: list[int]
    error: Incomplete

class OffsetAndMetadata(NamedTuple):
    offset: int
    metadata: str
    leader_epoch: int

class OffsetAndTimestamp(NamedTuple):
    offset: int
    timestamp: int
    leader_epoch: int

class MemberInformation(NamedTuple):
    member_id: str
    client_id: str
    client_host: str
    member_metadata: Incomplete
    member_assignment: Incomplete

class GroupInformation(NamedTuple):
    error_code: int
    group: str
    state: str
    protocol_type: str
    protocol: str
    members: list[MemberInformation]
    authorized_operations: list[str]

class RetryOptions(NamedTuple):
    limit: int
    backoff_ms: int
    retry_on_timeouts: bool
