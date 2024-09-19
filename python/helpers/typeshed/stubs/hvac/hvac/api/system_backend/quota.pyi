from _typeshed import Incomplete

from hvac.api.system_backend.system_backend_mixin import SystemBackendMixin

class Quota(SystemBackendMixin):
    def read_quota(self, name): ...
    def list_quotas(self): ...
    def create_or_update_quota(
        self,
        name,
        rate,
        path: Incomplete | None = None,
        interval: Incomplete | None = None,
        block_interval: Incomplete | None = None,
        role: Incomplete | None = None,
        rate_limit_type: Incomplete | None = None,
        inheritable: Incomplete | None = None,
    ): ...
    def delete_quota(self, name): ...
