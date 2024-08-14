from _typeshed import Incomplete

from hvac.api.system_backend.system_backend_mixin import SystemBackendMixin

class Health(SystemBackendMixin):
    def read_health_status(
        self,
        standby_ok: Incomplete | None = None,
        active_code: Incomplete | None = None,
        standby_code: Incomplete | None = None,
        dr_secondary_code: Incomplete | None = None,
        performance_standby_code: Incomplete | None = None,
        sealed_code: Incomplete | None = None,
        uninit_code: Incomplete | None = None,
        method: str = "HEAD",
    ): ...
