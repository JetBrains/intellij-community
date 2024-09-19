from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

DEFAULT_MOUNT_POINT: str

class Consul(VaultApiBase):
    def configure_access(self, address, token, scheme: Incomplete | None = None, mount_point="consul"): ...
    def create_or_update_role(
        self,
        name,
        policy: Incomplete | None = None,
        policies: Incomplete | None = None,
        token_type: Incomplete | None = None,
        local: Incomplete | None = None,
        ttl: Incomplete | None = None,
        max_ttl: Incomplete | None = None,
        mount_point="consul",
    ): ...
    def read_role(self, name, mount_point="consul"): ...
    def list_roles(self, mount_point="consul"): ...
    def delete_role(self, name, mount_point="consul"): ...
    def generate_credentials(self, name, mount_point="consul"): ...
