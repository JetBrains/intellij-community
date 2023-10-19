from _typeshed import Incomplete, SupportsLenAndGetItem
from collections.abc import Generator, Iterable
from logging import Logger
from typing import ClassVar, Protocol, TypeVar
from typing_extensions import TypeAlias

from .enums import EncryptionMethod
from .fpdf import FPDF
from .syntax import Name, PDFObject

_Key: TypeAlias = SupportsLenAndGetItem[int]
_T_co = TypeVar("_T_co", covariant=True)

LOGGER: Logger

import_error: ImportError | None

class _SupportsGetItem(Protocol[_T_co]):
    def __getitem__(self, __k: int) -> _T_co: ...

class ARC4:
    MOD: ClassVar[int]
    def KSA(self, key: _Key) -> list[int]: ...
    def PRGA(self, S: _SupportsGetItem[int]) -> Generator[int, None, None]: ...
    def encrypt(self, key: _Key, text: Iterable[int]) -> list[int]: ...

class CryptFilter:
    type: Name
    c_f_m: Name
    length: int
    def __init__(self, mode, length) -> None: ...
    def serialize(self) -> str: ...

class EncryptionDictionary(PDFObject):
    filter: Name
    length: int
    r: int
    o: str
    u: str
    v: int
    p: int
    encrypt_metadata: str  # not always defined
    c_f: str  # not always defined
    stm_f: Name
    str_f: Name
    def __init__(self, security_handler: StandardSecurityHandler) -> None: ...

class StandardSecurityHandler:
    DEFAULT_PADDING: ClassVar[bytes]
    fpdf: FPDF
    access_permission: int
    owner_password: str
    user_password: str
    encryption_method: EncryptionMethod | None
    cf: CryptFilter | None
    key_length: int
    v: int
    r: int
    encrypt_metadata: bool

    # The following fields are only defined after a call to generate_passwords().
    file_id: Incomplete
    info_id: Incomplete
    o: str
    k: str
    u: str

    def __init__(
        self,
        fpdf: FPDF,
        owner_password: str,
        user_password: str | None = None,
        permission: Incomplete | None = None,
        encryption_method: EncryptionMethod | None = None,
        encrypt_metadata: bool = False,
    ) -> None: ...
    def generate_passwords(self, file_id) -> None: ...
    def get_encryption_obj(self) -> EncryptionDictionary: ...
    def encrypt(self, text: str | bytes | bytearray, obj_id) -> bytes: ...
    def encrypt_string(self, string, obj_id): ...
    def encrypt_stream(self, stream, obj_id): ...
    def is_aes_algorithm(self) -> bool: ...
    def encrypt_bytes(self, data, obj_id) -> list[int]: ...
    def encrypt_AES_cryptography(self, key, data): ...
    def get_initialization_vector(self, size: int) -> bytearray: ...
    def padded_password(self, password: str) -> bytearray: ...
    def generate_owner_password(self) -> str: ...
    def generate_user_password(self) -> str: ...
    def generate_encryption_key(self) -> bytes: ...

def md5(data: bytes) -> bytes: ...
def int32(n: int) -> int: ...
