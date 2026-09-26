from _typeshed import Incomplete
from enum import IntEnum

from kafka.protocol.api_key import ApiKey
from kafka.util import EnumHelper

class ClusterAdminMixin:
    def describe_cluster(self) -> dict[str, Incomplete]: ...
    def describe_log_dirs(
        self,
        topic_partitions: dict[Incomplete, Incomplete] | list[Incomplete] | None = None,
        brokers: list[Incomplete] | None = None,
    ) -> list[dict[str, Incomplete]]: ...
    def alter_replica_log_dirs(self, replica_assignments): ...
    def describe_metadata_quorum(self): ...
    def get_broker_version_data(self, broker_id): ...
    def api_versions(self) -> dict[ApiKey, tuple[int, int] | Incomplete]: ...
    def describe_features(self, send_request_to_controller: bool = False) -> dict[str, Incomplete]: ...
    def update_features(
        self, feature_updates: dict[Incomplete, Incomplete], validate_only: bool = False, timeout_ms: int = 60000
    ) -> dict[Incomplete, Incomplete]: ...

class UpdateFeatureType(EnumHelper, IntEnum):
    UNKNOWN = 0
    UPGRADE = 1
    SAFE_DOWNGRADE = 2
    UNSAFE_DOWNGRADE = 3
