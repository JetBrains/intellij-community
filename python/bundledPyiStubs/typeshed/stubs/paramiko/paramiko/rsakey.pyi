from _typeshed import FileDescriptorOrPath, ReadableBuffer
from collections.abc import Callable
from typing import Final

from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKey, RSAPublicKey, RSAPublicNumbers
from cryptography.hazmat.primitives.hashes import HashAlgorithm
from paramiko.message import Message
from paramiko.pkey import PKey, _HasReadlines

class RSAKey(PKey):
    name: Final = "ssh-rsa"
    HASHES: Final[dict[str, type[HashAlgorithm]]]

    key: None | RSAPublicKey | RSAPrivateKey
    public_blob: None
    def __init__(
        self,
        msg: Message | None = None,
        data: ReadableBuffer | None = None,
        filename: FileDescriptorOrPath | None = None,
        password: str | None = None,
        key: None | RSAPublicKey | RSAPrivateKey = None,
        file_obj: _HasReadlines | None = None,
    ) -> None: ...
    @classmethod
    def identifiers(cls) -> list[str]: ...
    @property
    def size(self) -> int: ...
    @property
    def private_key(self) -> RSAPrivateKey | None: ...
    @property
    def public_numbers(self) -> RSAPublicNumbers: ...
    def asbytes(self) -> bytes: ...
    def get_name(self) -> str: ...
    def get_bits(self) -> int: ...
    def can_sign(self) -> bool: ...
    def sign_ssh_data(self, data: bytes, algorithm: str | None = None) -> Message: ...
    def verify_ssh_sig(self, data: bytes, msg: Message) -> bool: ...
    @staticmethod
    def generate(bits: int, progress_func: Callable[..., object] | None = None) -> RSAKey: ...
