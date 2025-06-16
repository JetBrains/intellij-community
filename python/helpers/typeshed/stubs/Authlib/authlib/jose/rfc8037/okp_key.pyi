from _typeshed import Incomplete

from authlib.jose.rfc7517 import AsymmetricKey

PUBLIC_KEYS_MAP: Incomplete
PRIVATE_KEYS_MAP: Incomplete

class OKPKey(AsymmetricKey):
    kty: str
    REQUIRED_JSON_FIELDS: Incomplete
    PUBLIC_KEY_FIELDS = REQUIRED_JSON_FIELDS
    PRIVATE_KEY_FIELDS: Incomplete
    PUBLIC_KEY_CLS: Incomplete
    PRIVATE_KEY_CLS: Incomplete
    SSH_PUBLIC_PREFIX: bytes
    def exchange_shared_key(self, pubkey): ...
    @staticmethod
    def get_key_curve(key): ...
    def load_private_key(self): ...
    def load_public_key(self): ...
    def dumps_private_key(self): ...
    def dumps_public_key(self, public_key=None): ...
    @classmethod
    def generate_key(cls, crv: str = "Ed25519", options=None, is_private: bool = False) -> OKPKey: ...
