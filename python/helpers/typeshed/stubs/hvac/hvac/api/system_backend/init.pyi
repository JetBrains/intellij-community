from _typeshed import Incomplete

from hvac.api.system_backend.system_backend_mixin import SystemBackendMixin

class Init(SystemBackendMixin):
    def read_init_status(self): ...
    def is_initialized(self): ...
    def initialize(
        self,
        secret_shares: Incomplete | None = None,
        secret_threshold: Incomplete | None = None,
        pgp_keys: Incomplete | None = None,
        root_token_pgp_key: Incomplete | None = None,
        stored_shares: Incomplete | None = None,
        recovery_shares: Incomplete | None = None,
        recovery_threshold: Incomplete | None = None,
        recovery_pgp_keys: Incomplete | None = None,
    ): ...
