from collections.abc import Sequence

class NewPartitions:
    total_count: int
    new_assignments: Sequence[Sequence[int]] | None
    def __init__(self, total_count: int, new_assignments: Sequence[Sequence[int]] | None = None) -> None: ...
