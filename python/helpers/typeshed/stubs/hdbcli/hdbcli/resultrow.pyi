from typing import Any

class ResultRow:
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    column_names: tuple[str, ...]
    column_values: tuple[Any, ...]
