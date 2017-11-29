from typing import Any, Union, Text

__revision__ = ...  # type: str

class XORCipher:
    block_size = ...  # type: int
    key_size = ...  # type: int
    def __init__(self, key: Union[bytes, Text], *args, **kwargs) -> None: ...
    def encrypt(self, plaintext: Union[bytes, Text]) -> bytes: ...
    def decrypt(self, ciphertext: bytes) -> bytes: ...


def new(key: Union[bytes, Text], *args, **kwargs) -> XORCipher: ...

block_size = ...  # type: int
key_size = ...  # type: int
