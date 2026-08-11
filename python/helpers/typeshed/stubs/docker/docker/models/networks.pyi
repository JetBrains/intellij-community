from _typeshed import Incomplete
from builtins import list as _list
from collections.abc import Iterable
from typing import Any, Literal

from docker.types import IPAMConfig

from .containers import Container
from .resource import Collection, Model

class Network(Model):
    @property
    def name(self) -> str | None: ...
    @property
    def containers(self) -> list[Container]: ...
    # Please keep in sync with docker.api.network.NetworkApiMixin.connect_container_to_network
    def connect(
        self,
        container: str | Container,
        ipv4_address=None,
        ipv6_address=None,
        aliases=None,
        links: dict[str, str] | dict[str, None] | dict[str, str | None] | Iterable[tuple[str, str | None]] | None = None,
        link_local_ips=None,
        driver_opt=None,
        mac_address=None,
    ) -> None: ...
    # Please keep in sync with docker.api.network.NetworkApiMixin.disconnect_container_from_network
    def disconnect(self, container: str | Container, force: bool = False) -> None: ...
    def remove(self) -> None: ...

class NetworkCollection(Collection[Network]):
    model: type[Network]
    # Please keep in sync with docker.api.network.NetworkApiMixin.create_network
    def create(  # type: ignore[override]
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
    ) -> Network: ...
    # Please keep in sync with docker.api.network.NetworkApiMixin.inspect_network
    def get(  # type: ignore[override]
        self, network_id: str, verbose: bool | None = None, scope: Literal["local", "global", "swarm"] | None = None
    ) -> Network: ...
    # Please keep in sync with docker.api.network.NetworkApiMixin.networks
    def list(
        self, names: _list[Incomplete] | None = None, ids: _list[Incomplete] | None = None, filters=None, *, greedy: bool = False
    ) -> _list[Network]: ...
    def prune(self, filters: dict[str, Incomplete] | None = None) -> dict[str, Any]: ...
