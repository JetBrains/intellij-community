from typing import Any, Union, Text
from .blockalgo import BlockAlgo

__revision__ = ...  # type: str

class DESCipher(BlockAlgo):
    def __init__(self, key: Union[bytes, Text], *args, **kwargs) -> None: ...

def new(key: Union[bytes, Text], *args, **kwargs) -> DESCipher: ...

MODE_ECB = ...  # type: int
MODE_CBC = ...  # type: int
MODE_CFB = ...  # type: int
MODE_PGP = ...  # type: int
MODE_OFB = ...  # type: int
MODE_CTR = ...  # type: int
MODE_OPENPGP = ...  # type: int
block_size = ...  # type: int
key_size = ...  # type: int
