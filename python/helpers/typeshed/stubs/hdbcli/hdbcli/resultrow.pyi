from typing import Any, Tuple

class ResultRow:
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    column_names: Tuple[str, ...]
    column_values: Tuple[Any, ...]
