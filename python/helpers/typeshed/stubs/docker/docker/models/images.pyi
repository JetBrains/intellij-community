from _typeshed import Incomplete, SupportsRead
from builtins import list as _list
from collections.abc import Iterator
from io import StringIO
from typing import IO, Any, Literal, TypedDict, overload, type_check_only

from docker._types import JSON
from docker.api.build import _Filers

from .resource import Collection, Model

@type_check_only
class _ContainerLimits(TypedDict, total=False):
    memory: int
    memswap: int
    cpushares: int
    cpusetcpus: str

class Image(Model):
    @property
    def labels(self) -> dict[str, Any]: ...
    @property
    def short_id(self) -> str: ...
    @property
    def tags(self) -> list[str]: ...
    def history(self) -> list[Any]: ...
    def remove(self, force: bool = False, noprune: bool = False) -> dict[str, Any]: ...
    def save(self, chunk_size: int = 2097152, named: str | bool = False) -> Iterator[Any]: ...
    # Please keep in sync with docker.api.image.ImageApiMixin.tag
    def tag(self, repository: str, tag: str | None = None, *, force: bool = False) -> bool: ...

class RegistryData(Model):
    image_name: str
    # Please keep in sync with docker.models.resource.Model.__init__
    def __init__(self, image_name: str, attrs=None, client=None, collection=None) -> None: ...
    @property
    def id(self) -> str: ...
    @property
    def short_id(self) -> str: ...
    def pull(self, platform: str | None = None) -> Image: ...
    def has_platform(self, platform): ...
    def reload(self) -> None: ...

class ImageCollection(Collection[Image]):
    model: type[Image]
    # Please keep in sync with docker.api.build.BuildApiMixin.build
    def build(
        self,
        *,
        path: str | None = None,
        tag: str | None = None,
        quiet: bool = False,
        fileobj: StringIO | IO[bytes] | None = None,
        nocache: bool = False,
        rm: bool = False,
        timeout: int | None = None,
        custom_context: bool = False,
        encoding: str | None = None,
        pull: bool = False,
        forcerm: bool = False,
        dockerfile: str | None = None,
        container_limits: _ContainerLimits | None = None,
        decode: bool = False,
        buildargs: dict[str, Any] | None = None,
        gzip: bool = False,
        shmsize: int | None = None,
        labels: dict[str, Any] | None = None,
        # need to use list, because the type must be json serializable
        cache_from: _list[str] | None = None,
        target: str | None = None,
        network_mode: str | None = None,
        squash: bool | None = None,
        extra_hosts: _list[str] | dict[str, str] | None = None,
        platform: str | None = None,
        isolation: str | None = None,
        use_config_proxy: bool = True,
    ) -> tuple[Image, Iterator[JSON]]: ...
    def get(self, name: str) -> Image: ...
    def get_registry_data(self, name, auth_config: dict[str, Any] | None = None) -> RegistryData: ...
    def list(self, name: str | None = None, all: bool = False, filters: dict[str, Any] | None = None) -> _list[Image]: ...
    def load(self, data: bytes | SupportsRead[bytes]) -> _list[Image]: ...

    # Please keep in sync with docker.api.image.ImageApiMixin.pull
    @overload
    def pull(
        self,
        repository: str,
        tag: str | None = None,
        all_tags: Literal[False] = False,
        *,
        platform: str | None = None,
        auth_config: dict[str, Any] | None = None,
    ) -> Image: ...
    @overload
    def pull(
        self,
        repository: str,
        tag: str | None = None,
        *,
        all_tags: Literal[True],
        auth_config: dict[str, Any] | None = None,
        platform: str | None = None,
    ) -> _list[Image]: ...
    @overload
    def pull(
        self,
        repository: str,
        tag: str | None,
        all_tags: Literal[True],
        *,
        auth_config: dict[str, Any] | None = None,
        platform: str | None = None,
    ) -> _list[Image]: ...

    # Please keep in sync with docker.api.image.ImageApiMixin.push
    def push(self, repository: str, tag: str | None = None, *, stream: bool = False, auth_config=None, decode: bool = False): ...
    # Please keep in sync with docker.api.image.ImageApiMixin.remove_image
    def remove(self, image: str, force: bool = False, noprune: bool = False) -> None: ...
    # Please keep in sync with docker.api.image.ImageApiMixin.search
    def search(self, term: str, limit: int | None = None): ...
    def prune(self, filters: dict[str, Any] | None = None): ...
    # Please keep in sync with docker.api.build.BuildApiMixin.prune_builds
    def prune_builds(
        self, filters: _Filers | None = None, keep_storage: int | None = None, all: bool | None = None
    ) -> dict[str, Incomplete]: ...

def normalize_platform(platform, engine_info): ...
