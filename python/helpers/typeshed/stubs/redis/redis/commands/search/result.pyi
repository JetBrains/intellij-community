from typing import Any

class Result:
    total: Any
    duration: Any
    docs: Any
    def __init__(self, res, hascontent, duration: int = ..., has_payload: bool = ..., with_scores: bool = ...) -> None: ...
