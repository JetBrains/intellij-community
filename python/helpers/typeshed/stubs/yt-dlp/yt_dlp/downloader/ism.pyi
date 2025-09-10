import struct
from _typeshed import SupportsWrite
from collections.abc import Collection, Mapping
from typing import Any

from .fragment import FragmentFD

u8: struct.Struct
u88: struct.Struct
u16: struct.Struct
u1616: struct.Struct
u32: struct.Struct
u64: struct.Struct
s88: struct.Struct
s16: struct.Struct
s1616: struct.Struct
s32: struct.Struct
unity_matrix: bytes
TRACK_ENABLED: int
TRACK_IN_MOVIE: int
TRACK_IN_PREVIEW: int
SELF_CONTAINED: int

def box(box_type: bytes, payload: bytes) -> bytes: ...
def full_box(box_type: bytes, version: int, flags: int, payload: bytes) -> bytes: ...
def write_piff_header(stream: SupportsWrite[bytes], params: Mapping[str, Any]) -> None: ...
def extract_box_data(data: bytes, box_sequence: Collection[bytes]) -> bytes | None: ...

class IsmFD(FragmentFD): ...
