from _typeshed import Incomplete

from authlib.jose.rfc7517 import AsymmetricKey
from cryptography.hazmat.primitives.asymmetric.ec import EllipticCurvePrivateKeyWithSerialization, EllipticCurvePublicKey

class ECKey(AsymmetricKey):
    kty: str
    DSS_CURVES: Incomplete
    CURVES_DSS: Incomplete
    REQUIRED_JSON_FIELDS: Incomplete
    PUBLIC_KEY_FIELDS = REQUIRED_JSON_FIELDS
    PRIVATE_KEY_FIELDS: Incomplete
    PUBLIC_KEY_CLS = EllipticCurvePublicKey
    PRIVATE_KEY_CLS = EllipticCurvePrivateKeyWithSerialization
    SSH_PUBLIC_PREFIX: bytes
    def exchange_shared_key(self, pubkey): ...
    @property
    def curve_key_size(self): ...
    def load_private_key(self): ...
    def load_public_key(self): ...
    def dumps_private_key(self): ...
    def dumps_public_key(self): ...
    @classmethod
    def generate_key(cls, crv: str = "P-256", options=None, is_private: bool = False) -> ECKey: ...
