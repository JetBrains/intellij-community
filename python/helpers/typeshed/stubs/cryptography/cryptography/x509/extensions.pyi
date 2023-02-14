from collections.abc import Iterator

from cryptography.x509 import GeneralName, ObjectIdentifier

class Extension:
    @property
    def value(self): ...

class GeneralNames:
    def __iter__(self) -> Iterator[GeneralName]: ...

class DistributionPoint:
    @property
    def full_name(self) -> GeneralNames: ...

class CRLDistributionPoints:
    def __iter__(self) -> Iterator[DistributionPoint]: ...

class AccessDescription:
    @property
    def access_method(self) -> ObjectIdentifier: ...
    @property
    def access_location(self) -> GeneralName: ...

class AuthorityInformationAccess:
    def __iter__(self) -> Iterator[AccessDescription]: ...
