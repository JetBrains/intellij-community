from _typeshed import Incomplete
from builtins import list as _list

from docker.types import DriverConfig

from .resource import Collection, Model

class Secret(Model):
    id_attribute: str
    @property
    def name(self): ...
    def remove(self): ...

class SecretCollection(Collection[Secret]):
    model: type[Secret]
    # Please keep in sync with docker.api.secret.SecretApiMixin.create_secret
    def create(  # type: ignore[override]
        self, *, name: str, data: bytes, labels: dict[str, Incomplete] | None = None, driver: DriverConfig | None = None
    ): ...
    def get(self, secret_id: str): ...
    # Please keep in sync with docker.api.secret.SecretApiMixin.secrets
    def list(self, *, filters: dict[str, Incomplete] | None = None) -> _list[Secret]: ...
