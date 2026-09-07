import codecs
from _codecs import _DecodeCharMap, _EncodeCharMap, _EncodingMap
from _typeshed import ReadableBuffer
from typing import ClassVar, Final, Literal

class Codec(codecs.Codec):
    encoding_table: ClassVar[_EncodeCharMap | Literal[""] | None]
    decoding_table: ClassVar[_DecodeCharMap | None]
    def encode(self, input: str, errors: str = "strict") -> tuple[bytes, int]: ...
    def decode(self, input: bytes, errors: str = "strict") -> tuple[str, int]: ...

class IncrementalEncoder(codecs.IncrementalEncoder):
    encoding_table: ClassVar[_EncodeCharMap | Literal[""] | None]
    def encode(self, input: str, final: bool = False) -> bytes: ...

class IncrementalDecoder(codecs.IncrementalDecoder):
    decoding_table: ClassVar[_DecodeCharMap | None]
    def decode(self, input: ReadableBuffer, final: bool = False) -> str: ...

class StreamWriter(Codec, codecs.StreamWriter): ...
class StreamReader(Codec, codecs.StreamReader): ...

user_decoding_table: Final[str]
user_encoding_table: Final[_EncodingMap]

class UserCodec(Codec):
    decoding_table: ClassVar[_DecodeCharMap]
    encoding_table: ClassVar[_EncodeCharMap]

class UserIncrementalEncoder(IncrementalEncoder):
    encoding_table: ClassVar[_EncodeCharMap]

class UserIncrementalDecoder(IncrementalDecoder):
    decoding_table: ClassVar[_DecodeCharMap]

user_codec_info: Final[codecs.CodecInfo]

class ReplacementCodec(Codec):
    decoding_table: ClassVar[_DecodeCharMap]
    encoding_table: ClassVar[Literal[""]]

class ReplacementIncrementalEncoder(IncrementalEncoder):
    encoding_table: ClassVar[Literal[""]]

class ReplacementIncrementalDecoder(IncrementalDecoder):
    decoding_table: ClassVar[_DecodeCharMap]

replacement_codec_info: Final[codecs.CodecInfo]
