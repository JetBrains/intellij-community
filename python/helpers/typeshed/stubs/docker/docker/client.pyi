from _typeshed import Incomplete
from collections.abc import Iterable, Mapping
from typing import Any, Literal, NoReturn, Protocol, overload, type_check_only

from docker import APIClient
from docker.models.configs import ConfigCollection
from docker.models.containers import ContainerCollection
from docker.models.images import ImageCollection
from docker.models.networks import NetworkCollection
from docker.models.nodes import NodeCollection
from docker.models.plugins import PluginCollection
from docker.models.secrets import SecretCollection
from docker.models.services import ServiceCollection
from docker.models.swarm import Swarm
from docker.models.volumes import VolumeCollection
from docker.tls import TLSConfig
from docker.types import CancellableStream

@type_check_only
class _Environ(Protocol):
    def __getitem__(self, k: str, /) -> str: ...
    def keys(self) -> Iterable[str]: ...

class DockerClient:
    api: APIClient
    # Please keep in sync with docker.APIClient
    def __init__(
        self,
        base_url: str | None = None,
        version: str | None = None,
        timeout: int = 60,
        tls: bool | TLSConfig = False,
        user_agent: str = ...,
        num_pools: int | None = None,
        credstore_env: Mapping[Incomplete, Incomplete] | None = None,
        use_ssh_client: bool = False,
        max_pool_size: int = 10,
    ) -> None: ...
    @classmethod
    def from_env(
        cls,
        *,
        version: str | None = None,
        timeout: int = 60,
        max_pool_size: int = 10,
        environment: _Environ | None = None,
        use_ssh_client: bool = False,
        use_context: bool = True,
    ) -> DockerClient: ...
    @classmethod
    def from_context(
        cls,
        name=None,
        *,
        version: str | None = None,
        timeout: int = 60,
        max_pool_size: int = 10,
        use_ssh_client: bool = False,
        base_url: str | None = None,
        tls: bool | TLSConfig = False,
        user_agent: str = ...,
        num_pools: int | None = None,
        credstore_env: Mapping[Incomplete, Incomplete] | None = None,
    ): ...
    @property
    def configs(self) -> ConfigCollection: ...
    @property
    def containers(self) -> ContainerCollection: ...
    @property
    def images(self) -> ImageCollection: ...
    @property
    def networks(self) -> NetworkCollection: ...
    @property
    def nodes(self) -> NodeCollection: ...
    @property
    def plugins(self) -> PluginCollection: ...
    @property
    def secrets(self) -> SecretCollection: ...
    @property
    def services(self) -> ServiceCollection: ...
    @property
    def swarm(self) -> Swarm: ...
    @property
    def volumes(self) -> VolumeCollection: ...

    @overload
    def events(self, *args, decode: Literal[False] | None = None, **kwargs) -> CancellableStream[str]: ...
    @overload
    def events(self, *args, decode: Literal[True] = ..., **kwargs) -> CancellableStream[dict[str, Any]]: ...

    def df(self) -> dict[str, Any]: ...
    def info(self) -> dict[str, Any]: ...
    def login(
        self,
        username: str,
        password: str | None = None,
        email: str | None = None,
        registry: str | None = None,
        reauth: bool = False,
        dockercfg_path: str | None = None,
    ) -> dict[str, Any]: ...
    def ping(self) -> bool: ...
    def version(self, api_version: bool = True) -> dict[str, Any]: ...
    def close(self) -> None: ...
    def __getattr__(self, name: str) -> NoReturn: ...

from_env = DockerClient.from_env
from_context = DockerClient.from_context
