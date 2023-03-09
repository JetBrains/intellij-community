from typing import Any
from typing_extensions import TypeAlias

# Enable when pyasn1 gets stubs:
# from pyasn1.type.univ import Sequence
_Sequence: TypeAlias = Any

class SicilyBindResponse(_Sequence):
    tagSet: Any
    componentType: Any

class DirSyncControlRequestValue(_Sequence):
    componentType: Any

class DirSyncControlResponseValue(_Sequence):
    componentType: Any

class SdFlags(_Sequence):
    componentType: Any

class ExtendedDN(_Sequence):
    componentType: Any

def dir_sync_control(criticality, object_security, ancestors_first, public_data_only, incremental_values, max_length, cookie): ...
def extended_dn_control(criticality: bool = ..., hex_format: bool = ...): ...
def show_deleted_control(criticality: bool = ...): ...
def security_descriptor_control(criticality: bool = ..., sdflags: int = ...): ...
def persistent_search_control(criticality: bool = ...): ...
