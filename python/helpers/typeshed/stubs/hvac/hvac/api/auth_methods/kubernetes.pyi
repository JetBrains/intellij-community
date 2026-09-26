from hvac.api.vault_api_base import VaultApiBase

DEFAULT_MOUNT_POINT: str

class Kubernetes(VaultApiBase):
    def configure(
        self,
        kubernetes_host: str,
        kubernetes_ca_cert: str | None = None,
        token_reviewer_jwt: str | None = None,
        pem_keys: list[str] | None = None,
        issuer: str | None = None,
        mount_point: str = "kubernetes",
        disable_local_ca_jwt: bool = False,
    ): ...
    def read_config(self, mount_point: str = "kubernetes"): ...
    def create_role(
        self,
        name: str,
        bound_service_account_names: list[str] | str,
        bound_service_account_namespaces: list[str] | str,
        ttl: str | None = None,
        max_ttl: str | None = None,
        period: str | None = None,
        policies: list[str] | str | None = None,
        token_type: str = "",
        mount_point: str = "kubernetes",
        alias_name_source: str | None = None,
        audience: str | None = None,
    ): ...
    def read_role(self, name: str, mount_point: str = "kubernetes"): ...
    def list_roles(self, mount_point: str = "kubernetes"): ...
    def delete_role(self, name: str, mount_point: str = "kubernetes"): ...
    def login(self, role: str, jwt: str, use_token: bool = True, mount_point: str = "kubernetes"): ...
