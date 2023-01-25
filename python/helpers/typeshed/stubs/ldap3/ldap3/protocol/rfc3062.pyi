from typing import Any
from typing_extensions import TypeAlias

# Enable when pyasn1 gets stubs:
# from pyasn1.type.univ import OctetString, Sequence
_OctetString: TypeAlias = Any
_Sequence: TypeAlias = Any

class UserIdentity(_OctetString):
    tagSet: Any
    encoding: str

class OldPasswd(_OctetString):
    tagSet: Any
    encoding: str

class NewPasswd(_OctetString):
    tagSet: Any
    encoding: str

class GenPasswd(_OctetString):
    tagSet: Any
    encoding: str

class PasswdModifyRequestValue(_Sequence):
    componentType: Any

class PasswdModifyResponseValue(_Sequence):
    componentType: Any
