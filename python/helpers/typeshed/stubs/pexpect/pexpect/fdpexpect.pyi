from _typeshed import Incomplete

from .spawnbase import SpawnBase

class fdspawn(SpawnBase):
    args: Incomplete
    command: Incomplete
    child_fd: Incomplete
    own_fd: bool
    closed: bool
    name: Incomplete
    use_poll: Incomplete
    def __init__(
        self,
        fd,
        args: Incomplete | None = None,
        timeout: int = 30,
        maxread: int = 2000,
        searchwindowsize: Incomplete | None = None,
        logfile: Incomplete | None = None,
        encoding: Incomplete | None = None,
        codec_errors: str = "strict",
        use_poll: bool = False,
    ) -> None: ...
    def close(self) -> None: ...
    def isalive(self) -> bool: ...
    def terminate(self, force: bool = False) -> None: ...
    def send(self, s: str | bytes) -> bytes: ...
    def sendline(self, s: str | bytes) -> bytes: ...
    def write(self, s) -> None: ...
    def writelines(self, sequence) -> None: ...
    def read_nonblocking(self, size: int = 1, timeout: int | None = -1) -> bytes: ...
