from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

logger: Incomplete

class Identity(VaultApiBase):
    def create_or_update_entity(
        self,
        name,
        entity_id: Incomplete | None = None,
        metadata: Incomplete | None = None,
        policies: Incomplete | None = None,
        disabled: Incomplete | None = None,
        mount_point: str = "identity",
    ): ...
    def create_or_update_entity_by_name(
        self,
        name,
        metadata: Incomplete | None = None,
        policies: Incomplete | None = None,
        disabled: Incomplete | None = None,
        mount_point: str = "identity",
    ): ...
    def read_entity(self, entity_id, mount_point: str = "identity"): ...
    def read_entity_by_name(self, name, mount_point: str = "identity"): ...
    def update_entity(
        self,
        entity_id,
        name: Incomplete | None = None,
        metadata: Incomplete | None = None,
        policies: Incomplete | None = None,
        disabled: Incomplete | None = None,
        mount_point: str = "identity",
    ): ...
    def delete_entity(self, entity_id, mount_point: str = "identity"): ...
    def delete_entity_by_name(self, name, mount_point: str = "identity"): ...
    def list_entities(self, method: str = "LIST", mount_point: str = "identity"): ...
    def list_entities_by_name(self, method: str = "LIST", mount_point: str = "identity"): ...
    def merge_entities(
        self,
        from_entity_ids,
        to_entity_id,
        force: Incomplete | None = None,
        mount_point: str = "identity",
        conflicting_alias_ids_to_keep: Incomplete | None = None,
    ): ...
    def create_or_update_entity_alias(
        self, name, canonical_id, mount_accessor, alias_id: Incomplete | None = None, mount_point: str = "identity"
    ): ...
    def read_entity_alias(self, alias_id, mount_point: str = "identity"): ...
    def update_entity_alias(self, alias_id, name, canonical_id, mount_accessor, mount_point: str = "identity"): ...
    def list_entity_aliases(self, method: str = "LIST", mount_point: str = "identity"): ...
    def delete_entity_alias(self, alias_id, mount_point: str = "identity"): ...
    @staticmethod
    def validate_member_id_params_for_group_type(group_type, params, member_group_ids, member_entity_ids): ...
    def create_or_update_group(
        self,
        name,
        group_id: Incomplete | None = None,
        group_type: str = "internal",
        metadata: Incomplete | None = None,
        policies: Incomplete | None = None,
        member_group_ids: Incomplete | None = None,
        member_entity_ids: Incomplete | None = None,
        mount_point: str = "identity",
    ): ...
    def read_group(self, group_id, mount_point: str = "identity"): ...
    def update_group(
        self,
        group_id,
        name,
        group_type: str = "internal",
        metadata: Incomplete | None = None,
        policies: Incomplete | None = None,
        member_group_ids: Incomplete | None = None,
        member_entity_ids: Incomplete | None = None,
        mount_point: str = "identity",
    ): ...
    def delete_group(self, group_id, mount_point: str = "identity"): ...
    def list_groups(self, method: str = "LIST", mount_point: str = "identity"): ...
    def list_groups_by_name(self, method: str = "LIST", mount_point: str = "identity"): ...
    def create_or_update_group_by_name(
        self,
        name,
        group_type: str = "internal",
        metadata: Incomplete | None = None,
        policies: Incomplete | None = None,
        member_group_ids: Incomplete | None = None,
        member_entity_ids: Incomplete | None = None,
        mount_point: str = "identity",
    ): ...
    def read_group_by_name(self, name, mount_point: str = "identity"): ...
    def delete_group_by_name(self, name, mount_point: str = "identity"): ...
    def create_or_update_group_alias(
        self,
        name,
        alias_id: Incomplete | None = None,
        mount_accessor: Incomplete | None = None,
        canonical_id: Incomplete | None = None,
        mount_point: str = "identity",
    ): ...
    def update_group_alias(
        self,
        entity_id,
        name,
        mount_accessor: Incomplete | None = None,
        canonical_id: Incomplete | None = None,
        mount_point="identity",
    ): ...
    def read_group_alias(self, alias_id, mount_point: str = "identity"): ...
    def delete_group_alias(self, entity_id, mount_point: str = "identity"): ...
    def list_group_aliases(self, method: str = "LIST", mount_point: str = "identity"): ...
    def lookup_entity(
        self,
        name: Incomplete | None = None,
        entity_id: Incomplete | None = None,
        alias_id: Incomplete | None = None,
        alias_name: Incomplete | None = None,
        alias_mount_accessor: Incomplete | None = None,
        mount_point: str = "identity",
    ): ...
    def lookup_group(
        self,
        name: Incomplete | None = None,
        group_id: Incomplete | None = None,
        alias_id: Incomplete | None = None,
        alias_name: Incomplete | None = None,
        alias_mount_accessor: Incomplete | None = None,
        mount_point: str = "identity",
    ): ...
    def configure_tokens_backend(self, issuer: Incomplete | None = None, mount_point: str = "identity"): ...
    def read_tokens_backend_configuration(self, mount_point: str = "identity"): ...
    def create_named_key(
        self,
        name,
        rotation_period: str = "24h",
        verification_ttl: str = "24h",
        allowed_client_ids: Incomplete | None = None,
        algorithm: str = "RS256",
        mount_point: str = "identity",
    ): ...
    def read_named_key(self, name, mount_point: str = "identity"): ...
    def delete_named_key(self, name, mount_point: str = "identity"): ...
    def list_named_keys(self, mount_point: str = "identity"): ...
    def rotate_named_key(self, name, verification_ttl, mount_point: str = "identity"): ...
    def create_or_update_role(
        self,
        name,
        key,
        template: Incomplete | None = None,
        client_id: Incomplete | None = None,
        ttl: str = "24h",
        mount_point: str = "identity",
    ): ...
    def read_role(self, name, mount_point: str = "identity"): ...
    def delete_role(self, name, mount_point: str = "identity"): ...
    def list_roles(self, mount_point: str = "identity"): ...
    def generate_signed_id_token(self, name, mount_point: str = "identity"): ...
    def introspect_signed_id_token(self, token, client_id: Incomplete | None = None, mount_point: str = "identity"): ...
    def read_well_known_configurations(self, mount_point: str = "identity"): ...
    def read_active_public_keys(self, mount_point: str = "identity"): ...
