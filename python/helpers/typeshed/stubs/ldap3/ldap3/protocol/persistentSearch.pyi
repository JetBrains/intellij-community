from typing import Any
from typing_extensions import TypeAlias

# Enable when pyasn1 gets stubs:
# from pyasn1.type.univ import Enumerated, Sequence
_Enumerated: TypeAlias = Any
_Sequence: TypeAlias = Any

class PersistentSearchControl(_Sequence):
    componentType: Any

class ChangeType(_Enumerated):
    namedValues: Any

class EntryChangeNotificationControl(_Sequence):
    componentType: Any

def persistent_search_control(change_types, changes_only: bool = ..., return_ecs: bool = ..., criticality: bool = ...): ...
