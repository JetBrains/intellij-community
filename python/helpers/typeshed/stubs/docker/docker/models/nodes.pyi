from builtins import list as _list
from typing import Any

from .resource import Collection, Model

class Node(Model):
    id_attribute: str
    @property
    def version(self): ...
    def update(self, node_spec): ...
    def remove(self, force: bool = False): ...

class NodeCollection(Collection[Node]):
    model: type[Node]
    def get(self, node_id): ...
    # Please keep in sync with docker.api.swarm.SwarmApiMixin.nodes
    def list(self, filters: dict[str, Any] | None = None) -> _list[Node]: ...  # Any: filter values + Node response
