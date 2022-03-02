from typing import Any

# Enable when pyasn1 gets stubs:
# from pyasn1.type.univ import Enumerated, Sequence
Enumerated = Any
Sequence = Any

class PersistentSearchControl(Sequence):
    componentType: Any

class ChangeType(Enumerated):
    namedValues: Any

class EntryChangeNotificationControl(Sequence):
    componentType: Any

def persistent_search_control(change_types, changes_only: bool = ..., return_ecs: bool = ..., criticality: bool = ...): ...
