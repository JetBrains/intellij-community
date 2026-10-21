from __future__ import annotations

import io
import sys
from _typeshed import ReadableBuffer
from bz2 import BZ2Decompressor
from lzma import LZMADecompressor
from typing import cast
from typing_extensions import assert_type
from zlib import decompressobj

if sys.version_info >= (3, 14):
    from compression._common._streams import DecompressReader, _Decompressor, _Reader
    from compression.zstd import ZstdDecompressor
else:
    from _compression import DecompressReader, _Decompressor, _Reader

###
# Tests for DecompressReader/_Decompressor
###


class CustomDecompressor:
    def decompress(self, data: ReadableBuffer, max_length: int = -1) -> bytes:
        return b""

    @property
    def unused_data(self) -> bytes:
        return b""

    @property
    def eof(self) -> bool:
        return False

    @property
    def needs_input(self) -> bool:
        return False


def accept_decompressor(d: _Decompressor) -> None:
    d.decompress(b"random bytes", 0)
    assert_type(d.eof, bool)
    assert_type(d.unused_data, bytes)


fp = cast(_Reader, io.BytesIO(b"hello world"))
DecompressReader(fp, decompressobj)
DecompressReader(fp, BZ2Decompressor)
DecompressReader(fp, LZMADecompressor)
DecompressReader(fp, CustomDecompressor)
accept_decompressor(decompressobj())
accept_decompressor(BZ2Decompressor())
accept_decompressor(LZMADecompressor())
accept_decompressor(CustomDecompressor())

if sys.version_info >= (3, 14):
    DecompressReader(fp, ZstdDecompressor)
    accept_decompressor(ZstdDecompressor())
