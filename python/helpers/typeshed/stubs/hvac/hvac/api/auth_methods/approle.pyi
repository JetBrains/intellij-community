from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

class AppRole(VaultApiBase):
    def create_or_update_approle(
        self,
        role_name,
        bind_secret_id: Incomplete | None = None,
        secret_id_bound_cidrs: Incomplete | None = None,
        secret_id_num_uses: Incomplete | None = None,
        secret_id_ttl: Incomplete | None = None,
        enable_local_secret_ids: Incomplete | None = None,
        token_ttl: Incomplete | None = None,
        token_max_ttl: Incomplete | None = None,
        token_policies: Incomplete | None = None,
        token_bound_cidrs: Incomplete | None = None,
        token_explicit_max_ttl: Incomplete | None = None,
        token_no_default_policy: Incomplete | None = None,
        token_num_uses: Incomplete | None = None,
        token_period: Incomplete | None = None,
        token_type: Incomplete | None = None,
        mount_point="approle",
    ): ...
    def list_roles(self, mount_point="approle"): ...
    def read_role(self, role_name, mount_point="approle"): ...
    def delete_role(self, role_name, mount_point="approle"): ...
    def read_role_id(self, role_name, mount_point="approle"): ...
    def update_role_id(self, role_name, role_id, mount_point="approle"): ...
    def generate_secret_id(
        self,
        role_name,
        metadata: Incomplete | None = None,
        cidr_list: Incomplete | None = None,
        token_bound_cidrs: Incomplete | None = None,
        mount_point="approle",
        wrap_ttl: Incomplete | None = None,
    ): ...
    def create_custom_secret_id(
        self,
        role_name,
        secret_id,
        metadata: Incomplete | None = None,
        cidr_list: Incomplete | None = None,
        token_bound_cidrs: Incomplete | None = None,
        mount_point="approle",
        wrap_ttl: Incomplete | None = None,
    ): ...
    def read_secret_id(self, role_name, secret_id, mount_point="approle"): ...
    def destroy_secret_id(self, role_name, secret_id, mount_point="approle"): ...
    def list_secret_id_accessors(self, role_name, mount_point="approle"): ...
    def read_secret_id_accessor(self, role_name, secret_id_accessor, mount_point="approle"): ...
    def destroy_secret_id_accessor(self, role_name, secret_id_accessor, mount_point="approle"): ...
    def login(self, role_id, secret_id: Incomplete | None = None, use_token: bool = True, mount_point="approle"): ...
