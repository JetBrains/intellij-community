from typing import Any

from docutils.nodes import Node, document

class Transform:
    def __init__(self, document: document, startnode: Node | None = ...): ...
    def __getattr__(self, __name: str) -> Any: ...  # incomplete

class Transformer:
    def __init__(self, document: document): ...
    def add_transform(self, transform_class: type[Transform], priority: int | None = ..., **kwargs) -> None: ...
    def __getattr__(self, __name: str) -> Any: ...  # incomplete

def __getattr__(name: str) -> Any: ...  # incomplete
