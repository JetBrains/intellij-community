from _typeshed import Incomplete
from typing import Any, Literal, TypedDict, type_check_only
from typing_extensions import TypeAlias

from docker.types import IPAMConfig

@type_check_only
class _HasId(TypedDict):
    Id: str

@type_check_only
class _HasID(TypedDict):
    ID: str

_Network: TypeAlias = _HasId | _HasID | str
_Container: TypeAlias = _HasId | _HasID | str

class NetworkApiMixin:
    def networks(self, names: Incomplete | None = None, ids: Incomplete | None = None, filters: Incomplete | None = None): ...
    def create_network(
        self,
        name: str,
        driver: str | None = None,
        options: dict[str, Any] | None = None,
        ipam: IPAMConfig | None = None,
        check_duplicate: bool | None = None,
        internal: bool = False,
        labels: dict[str, Any] | None = None,
        enable_ipv6: bool = False,
        attachable: bool | None = None,
        scope: Literal["local", "global", "swarm"] | None = None,
        ingress: bool | None = None,
    ) -> dict[str, str]: ...
    def prune_networks(self, filters: Incomplete | None = None): ...
    def remove_network(self, net_id: _Network) -> None: ...
    def inspect_network(
        self, net_id: _Network, verbose: bool | None = None, scope: Literal["local", "global", "swarm"] | None = None
    ): ...
    def connect_container_to_network(
        self,
        container: _Container,
        net_id: str,
        ipv4_address: Incomplete | None = None,
        ipv6_address: Incomplete | None = None,
        aliases: Incomplete | None = None,
        links: Incomplete | None = None,
        link_local_ips: Incomplete | None = None,
        driver_opt: Incomplete | None = None,
        mac_address: Incomplete | None = None,
    ) -> None: ...
    def disconnect_container_from_network(self, container: _Container, net_id: str, force: bool = False) -> None: ...
