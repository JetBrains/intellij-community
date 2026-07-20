from typing import Final, Protocol

class DecodeRatioError(Exception): ...
class DecodeLimitError(Exception): ...

class DecoderProtocol(Protocol):
    @property
    def needs_input(self) -> bool: ...
    def decode(self, b: bytes) -> bytes: ...
    def flush(self) -> bytes: ...
    def consume_bytes(self, data: bytes, chunk_size: int = 65536) -> bytes: ...

class ZlibDecoder(DecoderProtocol):
    __slots__ = ("_decoder",)
    WBITS_DEFLATE: Final = -15
    WBITS_ZLIB: Final = 15
    WBITS_GZIP: Final = 31
    WBITS_AUTO_GZIP_ZLIB: Final = 47

    def __init__(self, wbits: int = 47): ...
    @property
    def needs_input(self) -> bool: ...
    def decode(self, b: bytes) -> bytes: ...
    def flush(self) -> bytes: ...

def DeflateDecoder() -> ZlibDecoder: ...

class LimitDecoder(DecoderProtocol):
    __slots__ = (
        "_decoder",
        "_ratio",
        "_chunk_size",
        "_safe_limit",
        "_hard_limit",
        "_consumed_length",
        "_output_length",
        "_input_buffer",
        "_flushed",
    )

    def __init__(
        self,
        decoder: DecoderProtocol,
        ratio: float = 100,
        chunk_size: int = 65536,
        safe_limit: int = 10485760,
        hard_limit: int = ...,
    ) -> None: ...
    @property
    def needs_input(self) -> bool: ...
    def decode(self, b: bytes) -> bytes: ...
    def flush(self) -> bytes: ...
