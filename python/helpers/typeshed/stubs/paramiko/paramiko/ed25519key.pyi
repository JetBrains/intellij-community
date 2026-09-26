from _typeshed import FileDescriptorOrPath, ReadableBuffer
from typing import Any, Final, TypeAlias

from paramiko.message import Message
from paramiko.pkey import PKey, _HasReadlines

_VerifyKey: TypeAlias = Any  # actually nacl.signing.VerifyKey

class Ed25519Key(PKey):
    name: Final = "ssh-ed25519"

    public_blob: None

    def __init__(
        self,
        msg: Message | None = None,
        data: ReadableBuffer | None = None,
        filename: FileDescriptorOrPath | None = None,
        password: str | None = None,
        file_obj: _HasReadlines | None = None,
    ) -> None: ...
    def asbytes(self) -> bytes: ...
    def get_name(self) -> str: ...
    def get_bits(self) -> int: ...
    def can_sign(self) -> bool: ...
    def can_verify(self) -> bool: ...
    @property
    def verifying_key(self) -> _VerifyKey | None: ...
    def sign_ssh_data(self, data: bytes, algorithm: str | None = None) -> Message: ...
    def verify_ssh_sig(self, data: bytes, msg: Message) -> bool: ...
