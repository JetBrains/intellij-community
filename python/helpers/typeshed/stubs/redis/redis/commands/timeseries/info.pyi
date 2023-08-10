from typing import Any

class TSInfo:
    rules: list[Any]
    labels: list[Any]
    sourceKey: Any | None
    chunk_count: Any | None
    memory_usage: Any | None
    total_samples: Any | None
    retention_msecs: Any | None
    last_time_stamp: Any | None
    first_time_stamp: Any | None

    max_samples_per_chunk: Any | None
    chunk_size: Any | None
    duplicate_policy: Any | None
    def __init__(self, args) -> None: ...
