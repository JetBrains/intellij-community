from _typeshed import Incomplete
from typing import NamedTuple

log: Incomplete

class ConsumerPair(NamedTuple):
    src_member_id: Incomplete
    dst_member_id: Incomplete

def is_sublist(source, target): ...

class PartitionMovements:
    partition_movements_by_topic: Incomplete
    partition_movements: Incomplete
    def __init__(self) -> None: ...
    def move_partition(self, partition, old_consumer, new_consumer) -> None: ...
    def get_partition_to_be_moved(self, partition, old_consumer, new_consumer): ...
    def are_sticky(self): ...
