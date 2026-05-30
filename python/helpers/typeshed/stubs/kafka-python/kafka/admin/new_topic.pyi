from collections.abc import Mapping, Sequence

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
