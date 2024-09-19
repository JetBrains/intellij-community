from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

DEFAULT_MOUNT_POINT: str

class ActiveDirectory(VaultApiBase):
    def configure(
        self,
        binddn: Incomplete | None = None,
        bindpass: Incomplete | None = None,
        url: Incomplete | None = None,
        userdn: Incomplete | None = None,
        upndomain: Incomplete | None = None,
        ttl: Incomplete | None = None,
        max_ttl: Incomplete | None = None,
        mount_point="ad",
        *args,
        **kwargs,
    ): ...
    def read_config(self, mount_point="ad"): ...
    def create_or_update_role(
        self, name, service_account_name: Incomplete | None = None, ttl: Incomplete | None = None, mount_point="ad"
    ): ...
    def read_role(self, name, mount_point="ad"): ...
    def list_roles(self, mount_point="ad"): ...
    def delete_role(self, name, mount_point="ad"): ...
    def generate_credentials(self, name, mount_point="ad"): ...
