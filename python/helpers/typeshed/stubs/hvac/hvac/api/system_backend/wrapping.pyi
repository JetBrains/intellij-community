from _typeshed import Incomplete

from hvac.api.system_backend.system_backend_mixin import SystemBackendMixin

class Wrapping(SystemBackendMixin):
    def unwrap(self, token: Incomplete | None = None): ...
    def wrap(self, payload: dict[Incomplete, Incomplete] | None = None, ttl: int = 60): ...
