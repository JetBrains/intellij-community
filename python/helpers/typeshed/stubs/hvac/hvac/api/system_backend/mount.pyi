from _typeshed import Incomplete

from hvac.api.system_backend.system_backend_mixin import SystemBackendMixin

class Mount(SystemBackendMixin):
    def list_mounted_secrets_engines(self): ...
    def retrieve_mount_option(self, mount_point, option_name, default_value: Incomplete | None = None): ...
    def enable_secrets_engine(
        self,
        backend_type,
        path: Incomplete | None = None,
        description: Incomplete | None = None,
        config: Incomplete | None = None,
        plugin_name: Incomplete | None = None,
        options: Incomplete | None = None,
        local: bool = False,
        seal_wrap: bool = False,
        **kwargs,
    ): ...
    def disable_secrets_engine(self, path): ...
    def read_mount_configuration(self, path): ...
    def tune_mount_configuration(
        self,
        path,
        default_lease_ttl: Incomplete | None = None,
        max_lease_ttl: Incomplete | None = None,
        description: Incomplete | None = None,
        audit_non_hmac_request_keys: Incomplete | None = None,
        audit_non_hmac_response_keys: Incomplete | None = None,
        listing_visibility: Incomplete | None = None,
        passthrough_request_headers: Incomplete | None = None,
        options: Incomplete | None = None,
        force_no_cache: Incomplete | None = None,
        **kwargs,
    ): ...
    def move_backend(self, from_path, to_path): ...
