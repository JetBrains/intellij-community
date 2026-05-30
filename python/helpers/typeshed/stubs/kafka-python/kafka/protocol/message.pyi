from _typeshed import Incomplete

from kafka.protocol.struct import Struct
from kafka.protocol.types import AbstractType

class Message(Struct):
    SCHEMAS: Incomplete
    SCHEMA: Incomplete
    CODEC_MASK: int
    CODEC_GZIP: int
    CODEC_SNAPPY: int
    CODEC_LZ4: int
    CODEC_ZSTD: int
    TIMESTAMP_TYPE_MASK: int
    HEADER_SIZE: int
    timestamp: Incomplete
    crc: Incomplete
    magic: Incomplete
    attributes: Incomplete
    key: Incomplete
    value: Incomplete
    encode: Incomplete
    def __init__(self, value, key=None, magic: int = 0, attributes: int = 0, crc: int = 0, timestamp=None) -> None: ...
    @property
    def timestamp_type(self): ...
    @classmethod
    def decode(cls, data): ...
    def validate_crc(self): ...
    def is_compressed(self): ...
    def decompress(self): ...
    def __hash__(self): ...

class PartialMessage(bytes): ...

class MessageSet(AbstractType):
    ITEM: Incomplete
    HEADER_SIZE: int
    @classmethod
    def encode(cls, items, prepend_size: bool = True): ...
    @classmethod
    def decode(cls, data, bytes_to_read=None): ...
    @classmethod
    def repr(cls, messages): ...
