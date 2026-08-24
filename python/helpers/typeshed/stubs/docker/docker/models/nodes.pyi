from builtins import list as _list
from typing import Any, Literal

from .resource import Collection, Model

class Node(Model):
    id_attribute: str
    @property
    def version(self) -> int: ...
    def update(self, node_spec) -> Literal[True]: ...
    def remove(self, force: bool = False) -> Literal[True]: ...

class NodeCollection(Collection[Node]):
    model: type[Node]
    def get(self, node_id) -> Node: ...
    # Please keep in sync with docker.api.swarm.SwarmApiMixin.nodes
    def list(self, filters: dict[str, Any] | None = None) -> _list[Node]: ...  # Any: filter values + Node response
