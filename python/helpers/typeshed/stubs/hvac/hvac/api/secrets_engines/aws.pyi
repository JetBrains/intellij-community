from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

class Aws(VaultApiBase):
    def configure_root_iam_credentials(
        self,
        access_key,
        secret_key,
        region: Incomplete | None = None,
        iam_endpoint: Incomplete | None = None,
        sts_endpoint: Incomplete | None = None,
        max_retries: Incomplete | None = None,
        mount_point="aws",
    ): ...
    def rotate_root_iam_credentials(self, mount_point="aws"): ...
    def configure_lease(self, lease, lease_max, mount_point="aws"): ...
    def read_lease_config(self, mount_point="aws"): ...
    def create_or_update_role(
        self,
        name,
        credential_type,
        policy_document: Incomplete | None = None,
        default_sts_ttl: Incomplete | None = None,
        max_sts_ttl: Incomplete | None = None,
        role_arns: Incomplete | None = None,
        policy_arns: Incomplete | None = None,
        legacy_params: bool = False,
        iam_tags: Incomplete | None = None,
        mount_point="aws",
    ): ...
    def read_role(self, name, mount_point="aws"): ...
    def list_roles(self, mount_point="aws"): ...
    def delete_role(self, name, mount_point="aws"): ...
    def generate_credentials(
        self,
        name,
        role_arn: Incomplete | None = None,
        ttl: Incomplete | None = None,
        endpoint: str = "creds",
        mount_point="aws",
        role_session_name: Incomplete | None = None,
    ): ...
