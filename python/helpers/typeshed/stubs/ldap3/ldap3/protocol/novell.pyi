from typing import Any
from typing_extensions import TypeAlias

# Enable when pyasn1 gets stubs:
# from pyasn1.type.univ import Integer, OctetString, Sequence, SequenceOf
_Integer: TypeAlias = Any
_OctetString: TypeAlias = Any
_Sequence: TypeAlias = Any
_SequenceOf: TypeAlias = Any

NMAS_LDAP_EXT_VERSION: int

class Identity(_OctetString):
    encoding: str

class LDAPDN(_OctetString):
    tagSet: Any
    encoding: str

class Password(_OctetString):
    tagSet: Any
    encoding: str

class LDAPOID(_OctetString):
    tagSet: Any
    encoding: str

class GroupCookie(_Integer):
    tagSet: Any

class NmasVer(_Integer):
    tagSet: Any

class Error(_Integer):
    tagSet: Any

class NmasGetUniversalPasswordRequestValue(_Sequence):
    componentType: Any

class NmasGetUniversalPasswordResponseValue(_Sequence):
    componentType: Any

class NmasSetUniversalPasswordRequestValue(_Sequence):
    componentType: Any

class NmasSetUniversalPasswordResponseValue(_Sequence):
    componentType: Any

class ReplicaList(_SequenceOf):
    componentType: Any

class ReplicaInfoRequestValue(_Sequence):
    tagSet: Any
    componentType: Any

class ReplicaInfoResponseValue(_Sequence):
    tagSet: Any
    componentType: Any

class CreateGroupTypeRequestValue(_Sequence):
    componentType: Any

class CreateGroupTypeResponseValue(_Sequence):
    componentType: Any

class EndGroupTypeRequestValue(_Sequence):
    componentType: Any

class EndGroupTypeResponseValue(_Sequence):
    componentType: Any

class GroupingControlValue(_Sequence):
    componentType: Any
