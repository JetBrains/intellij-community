from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

DEFAULT_MOUNT_POINT: str

class Transit(VaultApiBase):
    def create_key(
        self,
        name,
        convergent_encryption: Incomplete | None = None,
        derived: Incomplete | None = None,
        exportable: Incomplete | None = None,
        allow_plaintext_backup: Incomplete | None = None,
        key_type: Incomplete | None = None,
        mount_point="transit",
        auto_rotate_period: Incomplete | None = None,
    ): ...
    def read_key(self, name, mount_point="transit"): ...
    def list_keys(self, mount_point="transit"): ...
    def delete_key(self, name, mount_point="transit"): ...
    def update_key_configuration(
        self,
        name,
        min_decryption_version: Incomplete | None = None,
        min_encryption_version: Incomplete | None = None,
        deletion_allowed: Incomplete | None = None,
        exportable: Incomplete | None = None,
        allow_plaintext_backup: Incomplete | None = None,
        mount_point="transit",
        auto_rotate_period: Incomplete | None = None,
    ): ...
    def rotate_key(self, name, mount_point="transit"): ...
    def export_key(self, name, key_type, version: Incomplete | None = None, mount_point="transit"): ...
    def encrypt_data(
        self,
        name,
        plaintext: Incomplete | None = None,
        context: Incomplete | None = None,
        key_version: Incomplete | None = None,
        nonce: Incomplete | None = None,
        batch_input: Incomplete | None = None,
        type: Incomplete | None = None,
        convergent_encryption: Incomplete | None = None,
        mount_point: str = "transit",
        associated_data: str | None = None,
    ): ...
    def decrypt_data(
        self,
        name,
        ciphertext: Incomplete | None = None,
        context: Incomplete | None = None,
        nonce: Incomplete | None = None,
        batch_input: Incomplete | None = None,
        mount_point: str = "transit",
        associated_data: str | None = None,
    ): ...
    def rewrap_data(
        self,
        name,
        ciphertext,
        context: Incomplete | None = None,
        key_version: Incomplete | None = None,
        nonce: Incomplete | None = None,
        batch_input: Incomplete | None = None,
        mount_point="transit",
    ): ...
    def generate_data_key(
        self,
        name,
        key_type,
        context: Incomplete | None = None,
        nonce: Incomplete | None = None,
        bits: Incomplete | None = None,
        mount_point="transit",
    ): ...
    def generate_random_bytes(
        self, n_bytes: Incomplete | None = None, output_format: Incomplete | None = None, mount_point="transit"
    ): ...
    def hash_data(
        self, hash_input, algorithm: Incomplete | None = None, output_format: Incomplete | None = None, mount_point="transit"
    ): ...
    def generate_hmac(
        self, name, hash_input, key_version: Incomplete | None = None, algorithm: Incomplete | None = None, mount_point="transit"
    ): ...
    def sign_data(
        self,
        name,
        hash_input: Incomplete | None = None,
        key_version: Incomplete | None = None,
        hash_algorithm: Incomplete | None = None,
        context: Incomplete | None = None,
        prehashed: Incomplete | None = None,
        signature_algorithm: Incomplete | None = None,
        marshaling_algorithm: Incomplete | None = None,
        salt_length: Incomplete | None = None,
        mount_point="transit",
        batch_input: Incomplete | None = None,
    ): ...
    def verify_signed_data(
        self,
        name,
        hash_input,
        signature: Incomplete | None = None,
        hmac: Incomplete | None = None,
        hash_algorithm: Incomplete | None = None,
        context: Incomplete | None = None,
        prehashed: Incomplete | None = None,
        signature_algorithm: Incomplete | None = None,
        salt_length: Incomplete | None = None,
        marshaling_algorithm: Incomplete | None = None,
        mount_point="transit",
    ): ...
    def backup_key(self, name, mount_point="transit"): ...
    def restore_key(self, backup, name: Incomplete | None = None, force: Incomplete | None = None, mount_point="transit"): ...
    def trim_key(self, name, min_version, mount_point="transit"): ...
