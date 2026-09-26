import uuid
from _typeshed import Incomplete
from collections.abc import Mapping, Sequence
from typing_extensions import deprecated

class TopicAdminMixin:
    config: dict[Incomplete, Incomplete]
    def list_topics(self) -> list[str]: ...
    def describe_topics(self, topics: Sequence[str | uuid.UUID] | None = None) -> list[dict[str, Incomplete]]: ...
    def create_topics(
        self,
        new_topics: dict[str, dict[Incomplete, Incomplete]] | Sequence[NewTopic],
        timeout_ms: float | None = None,
        validate_only: bool = False,
        raise_errors: bool = True,
        wait_for_metadata: bool = False,
    ): ...
    def wait_for_topics(self, topic_names, timeout_ms: float | None = 10000): ...
    def delete_topics(self, topics: Sequence[str | uuid.UUID], timeout_ms: float | None = None, raise_errors: bool = True): ...

@deprecated("Deprecated since v3.0.0. Use simple `dict` instead.")
class NewTopic:
    name: str
    num_partitions: int
    replication_factor: int
    replica_assignments: Mapping[int, Sequence[int]] | None
    topic_configs: Mapping[str, str] | None
    def __init__(
        self,
        name: str,
        num_partitions: int = -1,
        replication_factor: int = -1,
        replica_assignments: Mapping[int, Sequence[int]] | None = None,
        topic_configs: Mapping[str, str] | None = None,
    ) -> None: ...
