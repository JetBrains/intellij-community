from kafka.coordinator.assignors.abstract import AbstractPartitionAssignor
from kafka.protocol.consumer.metadata import ConsumerProtocolSubscription

class RoundRobinPartitionAssignor(AbstractPartitionAssignor):
    name: str
    version: int
    @classmethod
    def assign(cls, cluster, members): ...
    @classmethod
    def metadata(cls, topics) -> ConsumerProtocolSubscription: ...
    @classmethod
    def on_assignment(cls, assignment, generation) -> None: ...
