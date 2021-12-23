from typing import Any

# Enable when pyasn1 gets stubs:
# from pyasn1.type.univ import Integer, OctetString, Sequence, SequenceOf
Integer = Any
OctetString = Any
Sequence = Any
SequenceOf = Any

NMAS_LDAP_EXT_VERSION: int

class Identity(OctetString):
    encoding: str

class LDAPDN(OctetString):
    tagSet: Any
    encoding: str

class Password(OctetString):
    tagSet: Any
    encoding: str

class LDAPOID(OctetString):
    tagSet: Any
    encoding: str

class GroupCookie(Integer):
    tagSet: Any

class NmasVer(Integer):
    tagSet: Any

class Error(Integer):
    tagSet: Any

class NmasGetUniversalPasswordRequestValue(Sequence):
    componentType: Any

class NmasGetUniversalPasswordResponseValue(Sequence):
    componentType: Any

class NmasSetUniversalPasswordRequestValue(Sequence):
    componentType: Any

class NmasSetUniversalPasswordResponseValue(Sequence):
    componentType: Any

class ReplicaList(SequenceOf):
    componentType: Any

class ReplicaInfoRequestValue(Sequence):
    tagSet: Any
    componentType: Any

class ReplicaInfoResponseValue(Sequence):
    tagSet: Any
    componentType: Any

class CreateGroupTypeRequestValue(Sequence):
    componentType: Any

class CreateGroupTypeResponseValue(Sequence):
    componentType: Any

class EndGroupTypeRequestValue(Sequence):
    componentType: Any

class EndGroupTypeResponseValue(Sequence):
    componentType: Any

class GroupingControlValue(Sequence):
    componentType: Any
