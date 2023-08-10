from abc import ABCMeta
from enum import Enum
from typing import Any

from cryptography.hazmat.backends.interfaces import (
    DERSerializationBackend,
    DSABackend,
    EllipticCurveBackend,
    PEMSerializationBackend,
    RSABackend,
)

def load_pem_private_key(
    data: bytes, password: bytes | None, backend: PEMSerializationBackend | None = ...
) -> Any: ...  # actually RSAPrivateKey | DSAPrivateKey | DHPrivateKey | EllipticCurvePrivateKey
def load_pem_public_key(
    data: bytes, backend: PEMSerializationBackend | None = ...
) -> Any: ...  # actually RSAPublicKey | DSAPublicKey | DHPublicKey | EllipticCurvePublicKey
def load_der_private_key(
    data: bytes, password: bytes | None, backend: DERSerializationBackend | None = ...
) -> Any: ...  # actually RSAPrivateKey | DSAPrivateKey | DHPrivateKey | EllipticCurvePrivateKey
def load_der_public_key(
    data: bytes, backend: DERSerializationBackend | None = ...
) -> Any: ...  # actually RSAPublicKey | DSAPublicKey | DHPublicKey | EllipticCurvePublicKey
def load_ssh_public_key(
    data: bytes, backend: RSABackend | DSABackend | EllipticCurveBackend | None = ...
) -> Any: ...  # actually RSAPublicKey | DSAPublicKey | DHPublicKey | EllipticCurvePublicKey | Ed25519PublicKey

class Encoding(Enum):
    PEM: str
    DER: str
    OpenSSH: str
    Raw: str
    X962: str
    SMIME: str

class PrivateFormat(Enum):
    PKCS8: str
    TraditionalOpenSSL: str
    Raw: str
    OpenSSH: str

class PublicFormat(Enum):
    SubjectPublicKeyInfo: str
    PKCS1: str
    OpenSSH: str
    Raw: str
    CompressedPoint: str
    UncompressedPoint: str

class ParameterFormat(Enum):
    PKCS3: str

class KeySerializationEncryption(metaclass=ABCMeta): ...

class BestAvailableEncryption(KeySerializationEncryption):
    password: bytes
    def __init__(self, password: bytes) -> None: ...

class NoEncryption(KeySerializationEncryption): ...
