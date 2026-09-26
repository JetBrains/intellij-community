import sys
from _typeshed import Incomplete

from kafka.util import EnumHelper

class GroupAdminMixin:
    config: dict[Incomplete, Incomplete]
    def describe_groups(
        self, group_ids, group_coordinator_id: int | None = None, include_authorized_operations: bool = False
    ): ...
    def list_groups(self, broker_ids=None, states_filter=None, types_filter=None): ...
    def list_group_offsets(self, group_specs): ...
    def delete_groups(self, group_ids, group_coordinator_id: int | None = None): ...
    def alter_group_offsets(self, group_id: str, offsets, group_coordinator_id: int | None = None): ...
    def reset_group_offsets(self, group_id: str, offset_specs, group_coordinator_id: int | None = None): ...
    def delete_group_offsets(self, group_id: str, partitions, group_coordinator_id: int | None = None): ...
    def remove_group_members(self, group_id: str, members, group_coordinator_id: int | None = None): ...

class MemberToRemove:
    __slots__ = ("member_id", "group_instance_id", "reason")
    member_id: str | None
    group_instance_id: str | None
    reason: str | None
    def __init__(self, member_id: str | None = None, group_instance_id: str | None = None, reason: str | None = None) -> None: ...
    def __eq__(self, other: MemberToRemove) -> bool: ...  # type: ignore[override]
    def __hash__(self) -> int: ...

if sys.version_info >= (3, 11):
    from enum import StrEnum

    class GroupState(EnumHelper, StrEnum):
        UNKNOWN = "Unknown"
        PREPARING_REBALANCE = "PreparingRebalance"
        COMPLETING_REBALANCE = "CompletingRebalance"
        STABLE = "Stable"
        DEAD = "Dead"
        EMPTY = "Empty"
        ASSIGNING = "Assigning"
        RECONCILING = "Reconciling"

    class GroupType(EnumHelper, StrEnum):
        UNKNOWN = "Unknown"
        CLASSIC = "classic"
        CONSUMER = "consumer"
        SHARE = "share"

else:
    from enum import Enum

    class GroupState(EnumHelper, str, Enum):
        UNKNOWN = "Unknown"
        PREPARING_REBALANCE = "PreparingRebalance"
        COMPLETING_REBALANCE = "CompletingRebalance"
        STABLE = "Stable"
        DEAD = "Dead"
        EMPTY = "Empty"
        ASSIGNING = "Assigning"
        RECONCILING = "Reconciling"

    class GroupType(EnumHelper, str, Enum):
        UNKNOWN = "Unknown"
        CLASSIC = "classic"
        CONSUMER = "consumer"
        SHARE = "share"
