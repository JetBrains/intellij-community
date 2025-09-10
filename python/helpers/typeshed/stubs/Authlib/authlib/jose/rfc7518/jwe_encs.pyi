from _typeshed import Incomplete

from authlib.jose.rfc7516 import JWEEncAlgorithm

class CBCHS2EncAlgorithm(JWEEncAlgorithm):
    IV_SIZE: int
    name: Incomplete
    description: Incomplete
    key_size: Incomplete
    key_len: Incomplete
    CEK_SIZE: Incomplete
    hash_alg: Incomplete
    def __init__(self, key_size, hash_type) -> None: ...
    def encrypt(self, msg, aad, iv, key): ...
    def decrypt(self, ciphertext, aad, iv, tag, key): ...

class GCMEncAlgorithm(JWEEncAlgorithm):
    IV_SIZE: int
    name: Incomplete
    description: Incomplete
    key_size: Incomplete
    CEK_SIZE: Incomplete
    def __init__(self, key_size) -> None: ...
    def encrypt(self, msg, aad, iv, key): ...
    def decrypt(self, ciphertext, aad, iv, tag, key): ...

JWE_ENC_ALGORITHMS: Incomplete
