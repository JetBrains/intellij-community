from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

DEFAULT_MOUNT_POINT: str
logger: Incomplete

class Gcp(VaultApiBase):
    def configure(
        self,
        credentials: Incomplete | None = None,
        google_certs_endpoint="https://www.googleapis.com/oauth2/v3/certs",
        mount_point="gcp",
    ): ...
    def read_config(self, mount_point="gcp"): ...
    def delete_config(self, mount_point="gcp"): ...
    def create_role(
        self,
        name,
        role_type,
        project_id,
        ttl: Incomplete | None = None,
        max_ttl: Incomplete | None = None,
        period: Incomplete | None = None,
        policies: Incomplete | None = None,
        bound_service_accounts: Incomplete | None = None,
        max_jwt_exp: Incomplete | None = None,
        allow_gce_inference: Incomplete | None = None,
        bound_zones: Incomplete | None = None,
        bound_regions: Incomplete | None = None,
        bound_instance_groups: Incomplete | None = None,
        bound_labels: Incomplete | None = None,
        mount_point="gcp",
    ): ...
    def edit_service_accounts_on_iam_role(
        self, name, add: Incomplete | None = None, remove: Incomplete | None = None, mount_point="gcp"
    ): ...
    def edit_labels_on_gce_role(
        self, name, add: Incomplete | None = None, remove: Incomplete | None = None, mount_point="gcp"
    ): ...
    def read_role(self, name, mount_point="gcp"): ...
    def list_roles(self, mount_point="gcp"): ...
    def delete_role(self, role, mount_point="gcp"): ...
    def login(self, role, jwt, use_token: bool = True, mount_point="gcp"): ...
