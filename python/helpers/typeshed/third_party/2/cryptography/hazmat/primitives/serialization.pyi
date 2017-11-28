from typing import Any
from enum import Enum

def load_pem_private_key(data, password, backend): ...
def load_pem_public_key(data, backend): ...
def load_der_private_key(data, password, backend): ...
def load_der_public_key(data, backend): ...
def load_ssh_public_key(data, backend): ...

class Encoding(Enum):
    PEM = ...  # type: str
    DER = ...  # type: str

class PrivateFormat(Enum):
    PKCS8 = ...  # type: str
    TraditionalOpenSSL = ...  # type: str

class PublicFormat(Enum):
    SubjectPublicKeyInfo = ...  # type: str
    PKCS1 = ...  # type: str

class KeySerializationEncryption: ...

class BestAvailableEncryption:
    password = ...  # type: Any
    def __init__(self, password) -> None: ...

class NoEncryption: ...
