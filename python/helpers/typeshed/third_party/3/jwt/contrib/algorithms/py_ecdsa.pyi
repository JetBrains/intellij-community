from typing import Any
from jwt.algorithms import Algorithm

from . import _HashAlg

class ECAlgorithm(Algorithm):
    SHA256 = ...  # type: _HashAlg
    SHA384 = ...  # type: _HashAlg
    SHA512 = ...  # type: _HashAlg
    def __init__(self, hash_alg: _HashAlg) -> None: ...
