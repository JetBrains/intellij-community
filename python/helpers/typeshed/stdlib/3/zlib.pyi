# Stubs for zlib

DEFLATED = ...  # type: int
DEF_BUF_SIZE = ...  # type: int
DEF_MEM_LEVEL = ...  # type: int
MAX_WBITS = ...  # type: int
ZLIB_RUNTIME_VERSION = ...  # type: str
ZLIB_VERSION = ...  # type: str
Z_BEST_COMPRESSION = ...  # type: int
Z_BEST_SPEED = ...  # type: int
Z_DEFAULT_COMPRESSION = ...  # type: int
Z_DEFAULT_STRATEGY = ...  # type: int
Z_FILTERED = ...  # type: int
Z_FINISH = ...  # type: int
Z_FULL_FLUSH = ...  # type: int
Z_HUFFMAN_ONLY = ...  # type: int
Z_NO_FLUSH = ...  # type: int
Z_SYNC_FLUSH = ...  # type: int


class error(Exception): ...


class Compress:
    def compress(self, data: bytes) -> bytes: ...
    def flush(self, mode: int = ...) -> bytes: ...
    def copy(self) -> "Compress": ...


class Decompress:
    unused_data = ...  # type: bytes
    unconsumed_tail = ...  # type: bytes
    eof = ...  # type: bool
    def decompress(self, data: bytes, max_length: int = ...) -> bytes: ...
    def flush(self, length: int = ...) -> bytes: ...
    def copy(self) -> "Decompress": ...


def adler32(data: bytes, value: int = ...) -> int: ...
def compress(data: bytes, level: int = ...) -> bytes: ...
def compressobj(level: int = ..., method: int = ..., wbits: int = ...,
                memlevel: int = ..., strategy: int = ...,
                zdict: bytes = ...) -> Compress: ...
def crc32(data: bytes, value: int = ...) -> int: ...
def decompress(data: bytes, wbits: int = ..., bufsize: int = ...) -> bytes: ...
def decompressobj(wbits: int = ..., zdict: bytes = ...) -> Decompress: ...
