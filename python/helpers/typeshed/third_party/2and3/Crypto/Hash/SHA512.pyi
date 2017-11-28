from typing import Any, Optional
from Crypto.Hash.hashalgo import HashAlgo

class SHA512Hash(HashAlgo):
    oid = ...  # type: Any
    digest_size = ...  # type: int
    block_size = ...  # type: int
    def __init__(self, data: Optional[Any] = ...) -> None: ...
    def new(self, data: Optional[Any] = ...): ...

def new(data: Optional[Any] = ...): ...

digest_size = ...  # type: Any
