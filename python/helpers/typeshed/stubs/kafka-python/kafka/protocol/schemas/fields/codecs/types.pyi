import uuid
from _typeshed import Incomplete
from collections.abc import Iterable
from typing import ClassVar, Final, Literal

class FixedCodec:
    fmt: ClassVar[str | None]
    size: ClassVar[int | None]
    batchable: ClassVar[bool]
    def __init_subclass__(cls, **kw) -> None: ...
    @classmethod
    def encode(cls, value, compact: bool = False) -> bytes: ...
    @classmethod
    def encode_into(cls, out, value, compact: bool = False) -> None: ...
    @classmethod
    def decode(cls, data, compact: bool = False): ...
    @classmethod
    def decode_from(cls, data, pos) -> tuple[Incomplete, Incomplete]: ...
    @classmethod
    def emit_encode_into(cls, ctx, val_expr, indent, compact: bool = False) -> None: ...
    @classmethod
    def emit_decode_from(cls, ctx, var_name, indent, compact: bool = False) -> None: ...

class Int8(FixedCodec):
    fmt: ClassVar[str]
    size: ClassVar[int]

class Int16(FixedCodec):
    fmt: ClassVar[str]
    size: ClassVar[int]

class UnsignedInt16(FixedCodec):
    fmt: ClassVar[str]
    size: ClassVar[int]

class Int32(FixedCodec):
    fmt: ClassVar[str]
    size: ClassVar[int]

class Int64(FixedCodec):
    fmt: ClassVar[str]
    size: ClassVar[int]

class Float64(FixedCodec):
    fmt: ClassVar[str]
    size: ClassVar[int]

class UUID:
    fmt: ClassVar[str]
    size: ClassVar[int]
    ZERO_UUID: Final[uuid.UUID]
    @classmethod
    def encode(cls, value, compact: bool = False) -> bytes: ...
    @classmethod
    def encode_into(cls, out, value, compact: bool = False) -> None: ...
    @classmethod
    def emit_encode_into(cls, ctx, val_expr, indent, compact: bool = False) -> None: ...
    @classmethod
    def emit_decode_from(cls, ctx, var_name, indent, compact: bool = False) -> None: ...
    @classmethod
    def decode(cls, data, compact: bool = False) -> uuid.UUID | None: ...

class String:
    fmt: ClassVar[str | None]
    size: ClassVar[Literal["variable"]]
    encoding: str
    def __init__(self, encoding: str = "utf-8") -> None: ...
    def encode(self, value, compact: bool = False) -> bytes: ...
    def encode_into(self, out, value, compact: bool = False) -> None: ...
    def emit_encode_into(self, ctx, val_expr, indent, compact: bool = False) -> None: ...
    def emit_decode_from(self, ctx, var_name, indent, compact: bool = False) -> None: ...
    def decode(self, data, compact: bool = False): ...

class Bytes:
    fmt: ClassVar[str]
    size: ClassVar[Literal["variable"]]
    @classmethod
    def encode(cls, value, compact: bool = False) -> bytes: ...
    @classmethod
    def encode_into(cls, out, value, compact: bool = False) -> None: ...
    @classmethod
    def emit_encode_into(cls, ctx, val_expr, indent, compact: bool = False) -> None: ...
    @classmethod
    def emit_decode_from(cls, ctx, var_name, indent, compact: bool = False) -> None: ...
    @classmethod
    def decode(cls, data, compact: bool = False): ...

class Boolean(FixedCodec):
    fmt: ClassVar[str]
    size: ClassVar[int]

class UnsignedVarInt32:
    fmt: ClassVar[str]
    size: ClassVar[Literal["variable"]]
    @classmethod
    def decode(cls, data, compact: bool = False): ...
    @classmethod
    def encode(cls, value, compact: bool = False) -> bytes: ...
    @classmethod
    def encode_into(cls, out, value) -> None: ...
    @classmethod
    def emit_encode_into(cls, ctx, val_expr, indent, compact: bool = False) -> None: ...
    @classmethod
    def emit_decode_from(cls, ctx, var_name, indent) -> None: ...

class VarInt32:
    fmt: ClassVar[str]
    size: ClassVar[Literal["variable"]]
    @classmethod
    def decode(cls, data, compact: bool = False): ...
    @classmethod
    def encode(cls, value, compact: bool = False) -> bytes: ...

class VarInt64:
    fmt: ClassVar[str]
    size: ClassVar[Literal["variable"]]
    @classmethod
    def decode(cls, data, compact: bool = False): ...
    @classmethod
    def encode(cls, value, compact: bool = False) -> bytes: ...

class BitField:
    fmt: ClassVar[str]
    size: ClassVar[int]
    @classmethod
    def decode(cls, data, compact: bool = False) -> set[Incomplete] | None: ...
    @classmethod
    def encode(cls, vals, compact: bool = False) -> bytes: ...
    @classmethod
    def encode_into(cls, out, vals, compact: bool = False) -> None: ...
    @classmethod
    def emit_encode_into(cls, ctx, val_expr, indent, compact: bool = False) -> None: ...
    @classmethod
    def emit_decode_from(cls, ctx, var_name, indent, compact: bool = False) -> None: ...
    @classmethod
    def to_32_bit_field(cls, vals: Iterable[int]) -> int: ...
    @classmethod
    def from_32_bit_field(cls, value: int) -> set[int]: ...
