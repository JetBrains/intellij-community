from typing import Any, AnyStr, Tuple, Union

__all__ = ... # type: str

from asyncio import events
from asyncio import protocols
from asyncio import streams
from asyncio import transports
from asyncio.coroutines import coroutine


PIPE = ... # type: int
STDOUT = ... # type: int
DEVNULL = ... # type: int

class SubprocessStreamProtocol(streams.FlowControlMixin,
                               protocols.SubprocessProtocol):
    def __init__(self, limit: int, loop: events.AbstractEventLoop) -> None: ...
    def connection_made(self, transport: transports.BaseTransport) -> None: ...
    def pipe_data_received(self, fd: int, data: AnyStr) -> None: ...
    def pipe_connection_lost(self, fd: int, exc: Exception): ...
    def process_exited(self) -> None: ...


class Process:
    def __init__(self,
            transport: transports.BaseTransport,
            protocol: protocols.BaseProtocol,
            loop: events.AbstractEventLoop) -> None: ...
    @property
    def returncode(self) -> int: ...
    @coroutine
    def wait(self) -> int: ...
    def send_signal(self, signal: int) -> None: ...
    def terminate(self) -> None: ...
    def kill(self) -> None: ...
    @coroutine
    def communicate(self, input: bytes = ...) -> Tuple[bytes, bytes]: ...


@coroutine
def create_subprocess_shell(
        *Args: Union[str, bytes],  # Union used instead of AnyStr due to mypy issue #1236
        stdin: int = ...,
        stdout: int = ...,
        stderr: int = ...,
        loop: events.AbstractEventLoop = ...,
        limit: int = ...,
        **kwds: Any): ...

@coroutine
def create_subprocess_exec(
        program: Union[str, bytes],  # Union used instead of AnyStr due to mypy issue #1236
        *args: Any,
        stdin: int = ...,
        stdout: int = ...,
        stderr: int = ...,
        loop: events.AbstractEventLoop = ...,
        limit: int = ...,
        **kwds: Any) -> Process: ...
