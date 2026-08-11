from builtins import list as _list
from typing import Any

from .resource import Collection, Model

class Volume(Model):
    id_attribute: str
    @property
    def name(self) -> str: ...
    def remove(self, force: bool = False) -> None: ...

class VolumeCollection(Collection[Volume]):
    model: type[Volume]
    # Please keep in sync with docker.api.volume.VolumeApiMixin.create_volume
    def create(  # type: ignore[override]
        self,
        name: str | None = None,
        *,
        driver: str | None = None,
        driver_opts: dict[str, Any] | None = None,
        labels: dict[str, Any] | None = None,
    ) -> Volume: ...
    def get(self, volume_id: str) -> Volume: ...
    # Please keep in sync with docker.api.volume.VolumeApiMixin.create_volume
    def list(self, *, filters: dict[str, Any] | None = None) -> _list[Volume]: ...
    def prune(self, filters: dict[str, Any] | None = None) -> dict[str, Any]: ...
