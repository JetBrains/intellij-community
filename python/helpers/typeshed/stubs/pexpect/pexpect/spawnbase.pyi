from _typeshed import Incomplete
from collections.abc import Callable
from re import Pattern
from typing import AnyStr

PY3: bool
text_type: Callable[[Incomplete], Incomplete]

class _NullCoder:
    @staticmethod
    def encode(b: str, final: bool = False): ...
    @staticmethod
    def decode(b: str, final: bool = False): ...

class SpawnBase:
    encoding: Incomplete
    pid: Incomplete
    flag_eof: bool
    stdin: Incomplete
    stdout: Incomplete
    stderr: Incomplete
    searcher: Incomplete
    ignorecase: bool
    before: Incomplete
    after: Incomplete
    match: Incomplete
    match_index: Incomplete
    terminated: bool
    exitstatus: Incomplete
    signalstatus: Incomplete
    status: Incomplete
    child_fd: int
    timeout: Incomplete
    delimiter: Incomplete
    logfile: Incomplete
    logfile_read: Incomplete
    logfile_send: Incomplete
    maxread: Incomplete
    searchwindowsize: Incomplete
    delaybeforesend: float
    delayafterclose: float
    delayafterterminate: float
    delayafterread: float
    softspace: bool
    name: Incomplete
    closed: bool
    codec_errors: Incomplete
    string_type: Incomplete
    buffer_type: Incomplete
    crlf: bytes
    allowed_string_types: Incomplete
    linesep: Incomplete
    write_to_stdout: Incomplete
    async_pw_transport: Incomplete
    def __init__(
        self,
        timeout: int = 30,
        maxread: int = 2000,
        searchwindowsize: Incomplete | None = None,
        logfile: Incomplete | None = None,
        encoding: Incomplete | None = None,
        codec_errors: str = "strict",
    ) -> None: ...
    buffer: Incomplete
    def read_nonblocking(self, size: int = 1, timeout: int | None = None) -> bytes: ...
    def compile_pattern_list(self, patterns) -> list[Pattern[AnyStr]]: ...
    def expect(self, pattern, timeout: int = -1, searchwindowsize: int = -1, async_: bool = False, **kw) -> int: ...
    def expect_list(self, pattern_list, timeout: int = -1, searchwindowsize: int = -1, async_: bool = False, **kw) -> int: ...
    def expect_exact(self, pattern_list, timeout: int = -1, searchwindowsize: int = -1, async_: bool = False, **kw) -> int: ...
    def expect_loop(self, searcher, timeout: int = -1, searchwindowsize: int = -1) -> int: ...
    def read(self, size: int = -1) -> bytes: ...
    def readline(self, size: int = -1) -> bytes: ...
    def __iter__(self): ...
    def readlines(self, sizehint: int = -1) -> list[str]: ...
    def fileno(self): ...
    def flush(self) -> None: ...
    def isatty(self): ...
    def __enter__(self): ...
    def __exit__(self, etype, evalue, tb) -> None: ...
