from collections.abc import Callable
from typing import Any, TypeAlias

from google.protobuf.descriptor import Descriptor, FieldDescriptor
from google.protobuf.message import Message

_Decoder: TypeAlias = Callable[[memoryview, int, int, Message, dict[FieldDescriptor, Any]], int]
_NewDefault: TypeAlias = Callable[[Message], Any]

def IsDefaultScalarValue(value: Any) -> bool: ...
def ReadTag(buffer: memoryview, pos: int) -> tuple[bytes, int]: ...
def DecodeTag(tag_bytes: bytes) -> tuple[int, int]: ...
def EnumDecoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def Int32Decoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def Int64Decoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def UInt32Decoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def UInt64Decoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def SInt32Decoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def SInt64Decoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def Fixed32Decoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def Fixed64Decoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def SFixed32Decoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def SFixed64Decoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def FloatDecoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def DoubleDecoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def BoolDecoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def StringDecoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def BytesDecoder(
    field_number: int,
    is_repeated: bool,
    is_packed: bool,
    key: FieldDescriptor,
    new_default: _NewDefault,
    clear_if_default: bool = False,
) -> _Decoder: ...
def GroupDecoder(
    field_number: int, is_repeated: bool, is_packed: bool, key: FieldDescriptor, new_default: _NewDefault
) -> _Decoder: ...
def MessageDecoder(
    field_number: int, is_repeated: bool, is_packed: bool, key: FieldDescriptor, new_default: _NewDefault
) -> _Decoder: ...

MESSAGE_SET_ITEM_TAG: bytes

def MessageSetItemDecoder(descriptor: Descriptor) -> _Decoder: ...
def UnknownMessageSetItemDecoder() -> _Decoder: ...
def MapDecoder(field_descriptor: FieldDescriptor, new_default: _NewDefault, is_message_map: bool) -> _Decoder: ...

DEFAULT_RECURSION_LIMIT: int

def SetRecursionLimit(new_limit: int) -> None: ...
