from _typeshed import Incomplete

from kafka.coordinator.assignors.abstract import RebalanceProtocol
from kafka.coordinator.assignors.sticky.sticky_assignor import StickyAssignorMemberMetadataV1, StickyPartitionAssignor
from kafka.protocol.consumer.metadata import ConsumerProtocolAssignment, ConsumerProtocolSubscription

class CooperativeStickyAssignor(StickyPartitionAssignor):
    name: str
    version: int
    def supported_protocols(self) -> list[RebalanceProtocol]: ...
    def metadata(self, topics) -> ConsumerProtocolSubscription: ...
    @classmethod
    def parse_member_metadata(cls, metadata) -> StickyAssignorMemberMetadataV1: ...
    def assign(self, cluster, members) -> dict[Incomplete, ConsumerProtocolAssignment]: ...
