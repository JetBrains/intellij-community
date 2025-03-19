from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

DEFAULT_MOUNT_POINT: str

class Ldap(VaultApiBase):
    def configure(
        self,
        userdn: Incomplete | None = None,
        groupdn: Incomplete | None = None,
        url: Incomplete | None = None,
        case_sensitive_names: Incomplete | None = None,
        starttls: Incomplete | None = None,
        tls_min_version: Incomplete | None = None,
        tls_max_version: Incomplete | None = None,
        insecure_tls: Incomplete | None = None,
        certificate: Incomplete | None = None,
        binddn: Incomplete | None = None,
        bindpass: Incomplete | None = None,
        userattr: Incomplete | None = None,
        discoverdn: Incomplete | None = None,
        deny_null_bind: bool = True,
        upndomain: Incomplete | None = None,
        groupfilter: Incomplete | None = None,
        groupattr: Incomplete | None = None,
        use_token_groups: Incomplete | None = None,
        token_ttl: Incomplete | None = None,
        token_max_ttl: Incomplete | None = None,
        mount_point="ldap",
        *,
        anonymous_group_search: Incomplete | None = None,
        client_tls_cert: Incomplete | None = None,
        client_tls_key: Incomplete | None = None,
        connection_timeout: Incomplete | None = None,
        dereference_aliases: Incomplete | None = None,
        max_page_size: Incomplete | None = None,
        request_timeout: Incomplete | None = None,
        token_bound_cidrs: Incomplete | None = None,
        token_explicit_max_ttl: Incomplete | None = None,
        token_no_default_policy: Incomplete | None = None,
        token_num_uses: Incomplete | None = None,
        token_period: Incomplete | None = None,
        token_policies: Incomplete | None = None,
        token_type: Incomplete | None = None,
        userfilter: Incomplete | None = None,
        username_as_alias: Incomplete | None = None,
    ): ...
    def read_configuration(self, mount_point="ldap"): ...
    def create_or_update_group(self, name, policies: Incomplete | None = None, mount_point="ldap"): ...
    def list_groups(self, mount_point="ldap"): ...
    def read_group(self, name, mount_point="ldap"): ...
    def delete_group(self, name, mount_point="ldap"): ...
    def create_or_update_user(
        self, username, policies: Incomplete | None = None, groups: Incomplete | None = None, mount_point="ldap"
    ): ...
    def list_users(self, mount_point="ldap"): ...
    def read_user(self, username, mount_point="ldap"): ...
    def delete_user(self, username, mount_point="ldap"): ...
    def login(self, username, password, use_token: bool = True, mount_point="ldap"): ...
