from typing import NamedTuple, Any, Tuple

_int_type = int

class _UUIDFields(NamedTuple('_UUIDFields',
                             [('time_low', int), ('time_mid', int), ('time_hi_version', int), ('clock_seq_hi_variant', int), ('clock_seq_low', int), ('node', int)])):
    time = ...  # type: int
    clock_seq = ...  # type: int

class UUID:
    def __init__(self, hex: str = ..., bytes: str = ..., bytes_le: str = ...,
                  fields: Tuple[int, int, int, int, int, int] = ..., int: int = ..., version: Any = ...) -> None: ...
    bytes = ...  # type: str
    bytes_le = ...  # type: str
    fields = ...  # type: _UUIDFields
    hex = ...  # type: str
    int = ...  # type: _int_type
    urn = ...  # type: str
    variant = ...  # type: _int_type
    version = ...  # type: _int_type

RESERVED_NCS = ...  # type: int
RFC_4122 = ...  # type: int
RESERVED_MICROSOFT = ...  # type: int
RESERVED_FUTURE = ...  # type: int

def getnode() -> int: ...
def uuid1(node: int = ..., clock_seq: int = ...) -> UUID: ...
def uuid3(namespace: UUID, name: str) -> UUID: ...
def uuid4() -> UUID: ...
def uuid5(namespace: UUID, name: str) -> UUID: ...

NAMESPACE_DNS = ...  # type: UUID
NAMESPACE_URL = ...  # type: UUID
NAMESPACE_OID = ...  # type: UUID
NAMESPACE_X500 = ...  # type: UUID
