from _typeshed import Incomplete

from authlib.jose.rfc7516 import JWEEncAlgorithm

class XC20PEncAlgorithm(JWEEncAlgorithm):
    IV_SIZE: int
    name: str
    description: str
    key_size: Incomplete
    CEK_SIZE: Incomplete
    def __init__(self, key_size) -> None: ...
    def encrypt(self, msg, aad, iv, key): ...
    def decrypt(self, ciphertext, aad, iv, tag, key): ...
