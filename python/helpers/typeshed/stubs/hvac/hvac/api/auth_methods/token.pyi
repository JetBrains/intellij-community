from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

DEFAULT_MOUNT_POINT: str

class Token(VaultApiBase):
    def create(
        self,
        id: Incomplete | None = None,
        role_name: Incomplete | None = None,
        policies: Incomplete | None = None,
        meta: Incomplete | None = None,
        no_parent: bool = False,
        no_default_policy: bool = False,
        renewable: bool = True,
        ttl: Incomplete | None = None,
        type: Incomplete | None = None,
        explicit_max_ttl: Incomplete | None = None,
        display_name: str = "token",
        num_uses: int = 0,
        period: Incomplete | None = None,
        entity_alias: Incomplete | None = None,
        wrap_ttl: Incomplete | None = None,
        mount_point="token",
    ): ...
    def create_orphan(
        self,
        id: Incomplete | None = None,
        role_name: Incomplete | None = None,
        policies: Incomplete | None = None,
        meta: Incomplete | None = None,
        no_default_policy: bool = False,
        renewable: bool = True,
        ttl: Incomplete | None = None,
        type: Incomplete | None = None,
        explicit_max_ttl: Incomplete | None = None,
        display_name: str = "token",
        num_uses: int = 0,
        period: Incomplete | None = None,
        entity_alias: Incomplete | None = None,
        wrap_ttl: Incomplete | None = None,
        mount_point="token",
    ): ...
    def list_accessors(self, mount_point="token"): ...
    def lookup(self, token, mount_point="token"): ...
    def lookup_self(self, mount_point="token"): ...
    def lookup_accessor(self, accessor, mount_point="token"): ...
    def renew(self, token, increment: Incomplete | None = None, wrap_ttl: Incomplete | None = None, mount_point="token"): ...
    def renew_self(self, increment: Incomplete | None = None, wrap_ttl: Incomplete | None = None, mount_point="token"): ...
    def renew_accessor(
        self, accessor, increment: Incomplete | None = None, wrap_ttl: Incomplete | None = None, mount_point="token"
    ): ...
    def revoke(self, token, mount_point="token"): ...
    def revoke_self(self, mount_point="token"): ...
    def revoke_accessor(self, accessor, mount_point="token"): ...
    def revoke_and_orphan_children(self, token, mount_point="token"): ...
    def read_role(self, role_name, mount_point="token"): ...
    def list_roles(self, mount_point="token"): ...
    def create_or_update_role(
        self,
        role_name,
        allowed_policies: Incomplete | None = None,
        disallowed_policies: Incomplete | None = None,
        orphan: bool = False,
        renewable: bool = True,
        path_suffix: Incomplete | None = None,
        allowed_entity_aliases: Incomplete | None = None,
        mount_point="token",
        token_period: Incomplete | None = None,
        token_explicit_max_ttl: Incomplete | None = None,
    ): ...
    def delete_role(self, role_name, mount_point="token"): ...
    def tidy(self, mount_point="token"): ...
