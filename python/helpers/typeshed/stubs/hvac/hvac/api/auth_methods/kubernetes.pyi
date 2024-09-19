from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

DEFAULT_MOUNT_POINT: str

class Kubernetes(VaultApiBase):
    def configure(
        self,
        kubernetes_host,
        kubernetes_ca_cert: Incomplete | None = None,
        token_reviewer_jwt: Incomplete | None = None,
        pem_keys: Incomplete | None = None,
        issuer: Incomplete | None = None,
        mount_point="kubernetes",
        disable_local_ca_jwt: bool = False,
    ): ...
    def read_config(self, mount_point="kubernetes"): ...
    def create_role(
        self,
        name,
        bound_service_account_names,
        bound_service_account_namespaces,
        ttl: Incomplete | None = None,
        max_ttl: Incomplete | None = None,
        period: Incomplete | None = None,
        policies: Incomplete | None = None,
        token_type: str = "",
        mount_point="kubernetes",
        alias_name_source: Incomplete | None = None,
    ): ...
    def read_role(self, name, mount_point="kubernetes"): ...
    def list_roles(self, mount_point="kubernetes"): ...
    def delete_role(self, name, mount_point="kubernetes"): ...
    def login(self, role, jwt, use_token: bool = True, mount_point="kubernetes"): ...
