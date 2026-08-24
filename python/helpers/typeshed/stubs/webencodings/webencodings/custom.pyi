import codecs
from _codecs import _CharMap
from _typeshed import ReadableBuffer
from typing import ClassVar, Final

class Codec(codecs.Codec):
    encoding_table: ClassVar[_CharMap | str | None]
    decoding_table: ClassVar[_CharMap | str | None]
    def encode(self, input: str, errors: str = "strict") -> tuple[bytes, int]: ...
    def decode(self, input: bytes, errors: str = "strict") -> tuple[str, int]: ...

class IncrementalEncoder(codecs.IncrementalEncoder):
    encoding_table: ClassVar[_CharMap | str | None]
    def encode(self, input: str, final: bool = False) -> bytes: ...

class IncrementalDecoder(codecs.IncrementalDecoder):
    decoding_table: ClassVar[_CharMap | str | None]
    def decode(self, input: ReadableBuffer, final: bool = False) -> str: ...

class StreamWriter(Codec, codecs.StreamWriter): ...
class StreamReader(Codec, codecs.StreamReader): ...

user_decoding_table: Final[str]
user_encoding_table: Final[_CharMap]

class UserCodec(Codec):
    decoding_table: ClassVar[str]
    encoding_table: ClassVar[_CharMap]

class UserIncrementalEncoder(IncrementalEncoder):
    encoding_table: ClassVar[_CharMap]

class UserIncrementalDecoder(IncrementalDecoder):
    decoding_table: ClassVar[str]

user_codec_info: Final[codecs.CodecInfo]

class ReplacementCodec(Codec):
    decoding_table: ClassVar[str]
    encoding_table: ClassVar[str]

class ReplacementIncrementalEncoder(IncrementalEncoder):
    encoding_table: ClassVar[str]

class ReplacementIncrementalDecoder(IncrementalDecoder):
    decoding_table: ClassVar[str]

replacement_codec_info: Final[codecs.CodecInfo]
