from builtins import list as _list

from .resource import Collection, Model

class Service(Model):
    id_attribute: str
    @property
    def name(self): ...
    @property
    def version(self): ...
    def remove(self): ...
    def tasks(self, filters=None): ...
    def update(self, **kwargs): ...
    # Please keep in sync with docker.api.service.ServiceApiMixin.service_logs
    def logs(
        self,
        *,
        details: bool = False,
        follow: bool = False,
        stdout: bool = False,
        stderr: bool = False,
        since: int = 0,
        timestamps: bool = False,
        tail: str = "all",
    ): ...
    def scale(self, replicas: int | None): ...
    def force_update(self): ...

class ServiceCollection(Collection[Service]):
    model: type[Service]
    def create(self, image, command=None, **kwargs): ...  # type: ignore[override]
    def get(self, service_id, insert_defaults=None): ...
    # Please keep in sync with docker.api.service.ServiceApiMixin.services
    def list(self, *, filters=None, status=None) -> _list[Service]: ...

CONTAINER_SPEC_KWARGS: list[str]
TASK_TEMPLATE_KWARGS: list[str]
CREATE_SERVICE_KWARGS: list[str]
PLACEMENT_KWARGS: list[str]
