from typing import Any
from typing_extensions import TypeAlias

# Enable when pyasn1 gets stubs:
# from pyasn1.type import univ
_Sequence: TypeAlias = Any

RSA_ENCRYPTION_ASN1_OID: str

class RsaAlgorithmIdentifier(_Sequence):
    componentType: Any

class PKCS8PrivateKey(_Sequence):
    componentType: Any

class PublicKeyInfo(_Sequence):
    componentType: Any

def rsa_private_key_pkcs8_to_pkcs1(pkcs8_key): ...
def rsa_private_key_pkcs1_to_pkcs8(pkcs1_key): ...
def rsa_public_key_pkcs1_to_pkcs8(pkcs1_key): ...
def rsa_public_key_pkcs8_to_pkcs1(pkcs8_key): ...
