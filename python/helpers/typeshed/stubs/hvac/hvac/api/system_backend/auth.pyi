from _typeshed import Incomplete

from hvac.api.system_backend.system_backend_mixin import SystemBackendMixin

class Auth(SystemBackendMixin):
    def list_auth_methods(self): ...
    def enable_auth_method(
        self,
        method_type,
        description: Incomplete | None = None,
        config: Incomplete | None = None,
        plugin_name: Incomplete | None = None,
        local: bool = False,
        path: Incomplete | None = None,
        **kwargs,
    ): ...
    def disable_auth_method(self, path): ...
    def read_auth_method_tuning(self, path): ...
    def tune_auth_method(
        self,
        path,
        default_lease_ttl: Incomplete | None = None,
        max_lease_ttl: Incomplete | None = None,
        description: Incomplete | None = None,
        audit_non_hmac_request_keys: Incomplete | None = None,
        audit_non_hmac_response_keys: Incomplete | None = None,
        listing_visibility: Incomplete | None = None,
        passthrough_request_headers: Incomplete | None = None,
        **kwargs,
    ): ...
