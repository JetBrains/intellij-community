from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

DEFAULT_MOUNT_POINT: str
logger: Incomplete

class Azure(VaultApiBase):
    def configure(
        self,
        tenant_id,
        resource,
        environment: Incomplete | None = None,
        client_id: Incomplete | None = None,
        client_secret: Incomplete | None = None,
        mount_point="azure",
    ): ...
    def read_config(self, mount_point="azure"): ...
    def delete_config(self, mount_point="azure"): ...
    def create_role(
        self,
        name,
        policies: Incomplete | None = None,
        ttl: Incomplete | None = None,
        max_ttl: Incomplete | None = None,
        period: Incomplete | None = None,
        bound_service_principal_ids: Incomplete | None = None,
        bound_group_ids: Incomplete | None = None,
        bound_locations: Incomplete | None = None,
        bound_subscription_ids: Incomplete | None = None,
        bound_resource_groups: Incomplete | None = None,
        bound_scale_sets: Incomplete | None = None,
        num_uses: Incomplete | None = None,
        mount_point="azure",
    ): ...
    def read_role(self, name, mount_point="azure"): ...
    def list_roles(self, mount_point="azure"): ...
    def delete_role(self, name, mount_point="azure"): ...
    def login(
        self,
        role,
        jwt,
        subscription_id: Incomplete | None = None,
        resource_group_name: Incomplete | None = None,
        vm_name: Incomplete | None = None,
        vmss_name: Incomplete | None = None,
        use_token: bool = True,
        mount_point="azure",
    ): ...
