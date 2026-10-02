from _typeshed import Incomplete
from typing import ClassVar

from yaml.error import YAMLError
from yaml.nodes import Node

class SerializerError(YAMLError): ...

class Serializer:
    ANCHOR_TEMPLATE: ClassVar[str]
    use_encoding: Incomplete
    use_explicit_start: Incomplete
    use_explicit_end: Incomplete
    use_version: Incomplete
    use_tags: Incomplete
    serialized_nodes: dict[Incomplete, Incomplete]
    anchors: dict[Incomplete, Incomplete]
    last_anchor_id: int
    closed: bool | None
    def __init__(self, encoding=None, explicit_start=None, explicit_end=None, version=None, tags=None) -> None: ...
    def open(self) -> None: ...
    def close(self) -> None: ...
    def serialize(self, node: Node) -> None: ...
    def anchor_node(self, node) -> None: ...
    def generate_anchor(self, node) -> str: ...
    def serialize_node(self, node, parent, index) -> None: ...

__all__ = ["Serializer", "SerializerError"]
