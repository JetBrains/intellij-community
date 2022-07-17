from typing import Any

from cryptography.hazmat.primitives.asymmetric.dsa import DSAPrivateKeyWithSerialization
from cryptography.hazmat.primitives.asymmetric.ec import EllipticCurvePrivateKeyWithSerialization
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKeyWithSerialization
from cryptography.hazmat.primitives.serialization import KeySerializationEncryption
from cryptography.x509 import Certificate

def load_key_and_certificates(
    data: bytes, password: bytes | None, backend: Any | None = ...
) -> tuple[Any | None, Certificate | None, list[Certificate]]: ...
def serialize_key_and_certificates(
    name: bytes,
    key: RSAPrivateKeyWithSerialization | EllipticCurvePrivateKeyWithSerialization | DSAPrivateKeyWithSerialization,
    cert: Certificate | None,
    cas: list[Certificate] | None,
    enc: KeySerializationEncryption,
) -> bytes: ...
