from typing import Any, Union, Text

MODE_ECB = ...  # type: int
MODE_CBC = ...  # type: int
MODE_CFB = ...  # type: int
MODE_PGP = ...  # type: int
MODE_OFB = ...  # type: int
MODE_CTR = ...  # type: int
MODE_OPENPGP = ...  # type: int

class BlockAlgo:
    mode = ...  # type: int
    block_size = ...  # type: int
    IV = ...  # type: Any
    def __init__(self, factory: Any, key: Union[bytes, Text], *args, **kwargs) -> None: ...
    def encrypt(self, plaintext: Union[bytes, Text]) -> bytes: ...
    def decrypt(self, ciphertext: bytes) -> bytes: ...
