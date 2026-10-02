from _typeshed import Incomplete
from typing import Final, NamedTuple

from kafka.coordinator.assignors.abstract import AbstractPartitionAssignor
from kafka.protocol.consumer.metadata import ConsumerProtocolAssignment, ConsumerProtocolSubscription

class ConsumerGenerationPair(NamedTuple):
    consumer: Incomplete
    generation: Incomplete

def has_identical_list_elements(list_): ...
def subscriptions_comparator_key(element): ...
def partitions_comparator_key(element): ...
def remove_if_present(collection, element) -> None: ...

class StickyAssignorMemberMetadataV1(NamedTuple):
    subscription: Incomplete
    partitions: Incomplete
    generation: Incomplete

class StickyAssignmentExecutor:
    members: Incomplete
    current_assignment: Incomplete
    previous_assignment: Incomplete
    current_partition_consumer: Incomplete
    is_fresh_assignment: bool
    partition_to_all_potential_consumers: Incomplete
    consumer_to_all_potential_partitions: Incomplete
    sorted_current_subscriptions: Incomplete
    sorted_partitions: Incomplete
    unassigned_partitions: Incomplete
    revocation_required: bool
    partition_movements: Incomplete
    def __init__(self, cluster, members) -> None: ...
    def perform_initial_assignment(self) -> None: ...
    def balance(self) -> None: ...
    def get_final_assignment(self, member_id): ...

class StickyPartitionAssignor(AbstractPartitionAssignor):
    DEFAULT_GENERATION_ID: Final = -1
    name: str
    version: int
    member_assignment: Incomplete | None
    generation: int
    def __init__(self) -> None: ...
    def assign(self, cluster, members) -> dict[Incomplete, ConsumerProtocolAssignment]: ...
    @classmethod
    def parse_member_metadata(cls, metadata) -> StickyAssignorMemberMetadataV1: ...
    def metadata(self, topics) -> ConsumerProtocolSubscription: ...
    def on_assignment(self, assignment, generation) -> None: ...
