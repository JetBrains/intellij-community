from typing import Any

# Enable when pyasn1 gets stubs:
# from pyasn1.type import univ
Sequence = Any

RSA_ENCRYPTION_ASN1_OID: str

class RsaAlgorithmIdentifier(Sequence):
    componentType: Any

class PKCS8PrivateKey(Sequence):
    componentType: Any

class PublicKeyInfo(Sequence):
    componentType: Any

def rsa_private_key_pkcs8_to_pkcs1(pkcs8_key): ...
def rsa_private_key_pkcs1_to_pkcs8(pkcs1_key): ...
def rsa_public_key_pkcs1_to_pkcs8(pkcs1_key): ...
def rsa_public_key_pkcs8_to_pkcs1(pkcs8_key): ...
