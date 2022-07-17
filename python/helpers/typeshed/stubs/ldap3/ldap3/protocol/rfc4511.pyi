from typing import Any as _Any

# Enable when pyasn1 gets stubs:
# from pyasn1.type.univ import Boolean, Choice, Enumerated, Integer, Null, OctetString, Sequence, SequenceOf, SetOf
Boolean = _Any
Choice = _Any
Enumerated = _Any
Integer = _Any
Null = _Any
OctetString = _Any
Sequence = _Any
SequenceOf = _Any
SetOf = _Any

LDAP_MAX_INT: int
MAXINT: _Any
rangeInt0ToMaxConstraint: _Any
rangeInt1To127Constraint: _Any
size1ToMaxConstraint: _Any
responseValueConstraint: _Any
numericOIDConstraint: _Any
distinguishedNameConstraint: _Any
nameComponentConstraint: _Any
attributeDescriptionConstraint: _Any
uriConstraint: _Any
attributeSelectorConstraint: _Any

class Integer0ToMax(Integer):
    subtypeSpec: _Any

class LDAPString(OctetString):
    encoding: str

class MessageID(Integer0ToMax): ...
class LDAPOID(OctetString): ...
class LDAPDN(LDAPString): ...
class RelativeLDAPDN(LDAPString): ...
class AttributeDescription(LDAPString): ...

class AttributeValue(OctetString):
    encoding: str

class AssertionValue(OctetString):
    encoding: str

class AttributeValueAssertion(Sequence):
    componentType: _Any

class MatchingRuleId(LDAPString): ...

class Vals(SetOf):
    componentType: _Any

class ValsAtLeast1(SetOf):
    componentType: _Any
    subtypeSpec: _Any

class PartialAttribute(Sequence):
    componentType: _Any

class Attribute(Sequence):
    componentType: _Any

class AttributeList(SequenceOf):
    componentType: _Any

class Simple(OctetString):
    tagSet: _Any
    encoding: str

class Credentials(OctetString):
    encoding: str

class SaslCredentials(Sequence):
    tagSet: _Any
    componentType: _Any

class SicilyPackageDiscovery(OctetString):
    tagSet: _Any
    encoding: str

class SicilyNegotiate(OctetString):
    tagSet: _Any
    encoding: str

class SicilyResponse(OctetString):
    tagSet: _Any
    encoding: str

class AuthenticationChoice(Choice):
    componentType: _Any

class Version(Integer):
    subtypeSpec: _Any

class ResultCode(Enumerated):
    namedValues: _Any
    subTypeSpec: _Any

class URI(LDAPString): ...

class Referral(SequenceOf):
    tagSet: _Any
    componentType: _Any

class ServerSaslCreds(OctetString):
    tagSet: _Any
    encoding: str

class LDAPResult(Sequence):
    componentType: _Any

class Criticality(Boolean):
    defaultValue: bool

class ControlValue(OctetString):
    encoding: str

class Control(Sequence):
    componentType: _Any

class Controls(SequenceOf):
    tagSet: _Any
    componentType: _Any

class Scope(Enumerated):
    namedValues: _Any

class DerefAliases(Enumerated):
    namedValues: _Any

class TypesOnly(Boolean): ...
class Selector(LDAPString): ...

class AttributeSelection(SequenceOf):
    componentType: _Any

class MatchingRule(MatchingRuleId):
    tagSet: _Any

class Type(AttributeDescription):
    tagSet: _Any

class MatchValue(AssertionValue):
    tagSet: _Any

class DnAttributes(Boolean):
    tagSet: _Any
    defaultValue: _Any

class MatchingRuleAssertion(Sequence):
    componentType: _Any

class Initial(AssertionValue):
    tagSet: _Any

class Any(AssertionValue):
    tagSet: _Any

class Final(AssertionValue):
    tagSet: _Any

class Substring(Choice):
    componentType: _Any

class Substrings(SequenceOf):
    subtypeSpec: _Any
    componentType: _Any

class SubstringFilter(Sequence):
    tagSet: _Any
    componentType: _Any

class And(SetOf):
    tagSet: _Any
    subtypeSpec: _Any

class Or(SetOf):
    tagSet: _Any
    subtypeSpec: _Any

class Not(Choice): ...

class EqualityMatch(AttributeValueAssertion):
    tagSet: _Any

class GreaterOrEqual(AttributeValueAssertion):
    tagSet: _Any

class LessOrEqual(AttributeValueAssertion):
    tagSet: _Any

class Present(AttributeDescription):
    tagSet: _Any

class ApproxMatch(AttributeValueAssertion):
    tagSet: _Any

class ExtensibleMatch(MatchingRuleAssertion):
    tagSet: _Any

class Filter(Choice):
    componentType: _Any

class PartialAttributeList(SequenceOf):
    componentType: _Any

class Operation(Enumerated):
    namedValues: _Any

class Change(Sequence):
    componentType: _Any

class Changes(SequenceOf):
    componentType: _Any

class DeleteOldRDN(Boolean): ...

class NewSuperior(LDAPDN):
    tagSet: _Any

class RequestName(LDAPOID):
    tagSet: _Any

class RequestValue(OctetString):
    tagSet: _Any
    encoding: str

class ResponseName(LDAPOID):
    tagSet: _Any

class ResponseValue(OctetString):
    tagSet: _Any
    encoding: str

class IntermediateResponseName(LDAPOID):
    tagSet: _Any

class IntermediateResponseValue(OctetString):
    tagSet: _Any
    encoding: str

class BindRequest(Sequence):
    tagSet: _Any
    componentType: _Any

class BindResponse(Sequence):
    tagSet: _Any
    componentType: _Any

class UnbindRequest(Null):
    tagSet: _Any

class SearchRequest(Sequence):
    tagSet: _Any
    componentType: _Any

class SearchResultReference(SequenceOf):
    tagSet: _Any
    subtypeSpec: _Any
    componentType: _Any

class SearchResultEntry(Sequence):
    tagSet: _Any
    componentType: _Any

class SearchResultDone(LDAPResult):
    tagSet: _Any

class ModifyRequest(Sequence):
    tagSet: _Any
    componentType: _Any

class ModifyResponse(LDAPResult):
    tagSet: _Any

class AddRequest(Sequence):
    tagSet: _Any
    componentType: _Any

class AddResponse(LDAPResult):
    tagSet: _Any

class DelRequest(LDAPDN):
    tagSet: _Any

class DelResponse(LDAPResult):
    tagSet: _Any

class ModifyDNRequest(Sequence):
    tagSet: _Any
    componentType: _Any

class ModifyDNResponse(LDAPResult):
    tagSet: _Any

class CompareRequest(Sequence):
    tagSet: _Any
    componentType: _Any

class CompareResponse(LDAPResult):
    tagSet: _Any

class AbandonRequest(MessageID):
    tagSet: _Any

class ExtendedRequest(Sequence):
    tagSet: _Any
    componentType: _Any

class ExtendedResponse(Sequence):
    tagSet: _Any
    componentType: _Any

class IntermediateResponse(Sequence):
    tagSet: _Any
    componentType: _Any

class ProtocolOp(Choice):
    componentType: _Any

class LDAPMessage(Sequence):
    componentType: _Any
