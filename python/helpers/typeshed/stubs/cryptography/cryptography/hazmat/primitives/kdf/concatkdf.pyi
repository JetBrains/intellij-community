from cryptography.hazmat.backends.interfaces import HashBackend, HMACBackend
from cryptography.hazmat.primitives.hashes import HashAlgorithm
from cryptography.hazmat.primitives.kdf import KeyDerivationFunction

class ConcatKDFHash(KeyDerivationFunction):
    def __init__(self, algorithm: HashAlgorithm, length: int, otherinfo: bytes | None, backend: HashBackend | None = ...): ...
    def derive(self, key_material: bytes) -> bytes: ...
    def verify(self, key_material: bytes, expected_key: bytes) -> None: ...

class ConcatKDFHMAC(KeyDerivationFunction):
    def __init__(
        self,
        algorithm: HashAlgorithm,
        length: int,
        salt: bytes | None,
        otherinfo: bytes | None,
        backend: HMACBackend | None = ...,
    ): ...
    def derive(self, key_material: bytes) -> bytes: ...
    def verify(self, key_material: bytes, expected_key: bytes) -> None: ...
