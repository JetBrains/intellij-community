from _typeshed import Incomplete

from kafka.coordinator.assignors.abstract import AbstractPartitionAssignor

log: Incomplete

class RoundRobinPartitionAssignor(AbstractPartitionAssignor):
    name: str
    version: int
    @classmethod
    def assign(cls, cluster, group_subscriptions): ...
    @classmethod
    def metadata(cls, topics): ...
    @classmethod
    def on_assignment(cls, assignment) -> None: ...
