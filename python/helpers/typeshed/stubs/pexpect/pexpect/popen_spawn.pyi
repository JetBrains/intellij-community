from _typeshed import Incomplete

from .spawnbase import SpawnBase

class PopenSpawn(SpawnBase):
    crlf: Incomplete
    proc: Incomplete
    pid: Incomplete
    closed: bool
    def __init__(
        self,
        cmd,
        timeout: int = 30,
        maxread: int = 2000,
        searchwindowsize: Incomplete | None = None,
        logfile: Incomplete | None = None,
        cwd: Incomplete | None = None,
        env: Incomplete | None = None,
        encoding: Incomplete | None = None,
        codec_errors: str = "strict",
        preexec_fn: Incomplete | None = None,
    ) -> None: ...
    flag_eof: bool
    def read_nonblocking(self, size, timeout): ...
    def write(self, s) -> None: ...
    def writelines(self, sequence) -> None: ...
    def send(self, s): ...
    def sendline(self, s: str = ""): ...
    exitstatus: Incomplete
    signalstatus: Incomplete
    terminated: bool
    def wait(self): ...
    def kill(self, sig) -> None: ...
    def sendeof(self) -> None: ...
