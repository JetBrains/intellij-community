from cryptography.hazmat.backends.interfaces import HMACBackend
from cryptography.hazmat.primitives.hashes import HashAlgorithm

class TOTP(object):
    def __init__(
        self,
        key: bytes,
        length: int,
        algorithm: HashAlgorithm,
        time_step: int,
        backend: HMACBackend | None = ...,
        enforce_key_length: bool = ...,
    ): ...
    def generate(self, time: int) -> bytes: ...
    def get_provisioning_uri(self, account_name: str, issuer: str | None) -> str: ...
    def verify(self, totp: bytes, time: int) -> None: ...
