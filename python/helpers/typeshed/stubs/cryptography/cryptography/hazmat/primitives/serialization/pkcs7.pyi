from enum import Enum
from typing import Any, Iterable, List, Optional, Union

from cryptography.hazmat.primitives.asymmetric.ec import EllipticCurvePrivateKey
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKey
from cryptography.hazmat.primitives.hashes import SHA1, SHA224, SHA256, SHA384, SHA512
from cryptography.hazmat.primitives.serialization import Encoding
from cryptography.x509 import Certificate

def load_pem_pkcs7_certificates(data: bytes) -> List[Certificate]: ...
def load_der_pkcs7_certificates(data: bytes) -> List[Certificate]: ...

class PKCS7Options(Enum):
    Text: str
    Binary: str
    DetachedSignature: str
    NoCapabilities: str
    NoAttributes: str
    NoCerts: str

class PKCS7SignatureBuilder:
    def set_data(self, data: bytes) -> PKCS7SignatureBuilder: ...
    def add_signer(
        self,
        certificate: Certificate,
        private_key: Union[RSAPrivateKey, EllipticCurvePrivateKey],
        hash_algorithm: Union[SHA1, SHA224, SHA256, SHA384, SHA512],
    ) -> PKCS7SignatureBuilder: ...
    def add_certificate(self, certificate: Certificate) -> PKCS7SignatureBuilder: ...
    def sign(self, encoding: Encoding, options: Iterable[PKCS7Options], backend: Optional[Any] = ...) -> bytes: ...
