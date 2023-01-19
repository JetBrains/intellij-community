from typing import Any

class Node:
    id: Any
    alias: Any
    label: Any
    labels: Any
    properties: Any
    def __init__(
        self,
        node_id: Any | None = ...,
        alias: Any | None = ...,
        label: str | list[str] | None = ...,
        properties: Any | None = ...,
    ) -> None: ...
    def to_string(self): ...
    def __eq__(self, rhs): ...
