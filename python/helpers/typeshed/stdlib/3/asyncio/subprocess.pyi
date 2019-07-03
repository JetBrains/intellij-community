from asyncio import events
from asyncio import protocols
from asyncio import streams
from asyncio import transports
from asyncio.coroutines import coroutine
from typing import Any, Generator, List, Optional, Text, Tuple, Union, IO

__all__: List[str]

PIPE: int
STDOUT: int
DEVNULL: int

class SubprocessStreamProtocol(streams.FlowControlMixin,
                               protocols.SubprocessProtocol):
    stdin: Optional[streams.StreamWriter]
    stdout: Optional[streams.StreamReader]
    stderr: Optional[streams.StreamReader]
    def __init__(self, limit: int, loop: events.AbstractEventLoop) -> None: ...
    def connection_made(self, transport: transports.BaseTransport) -> None: ...
    def pipe_data_received(self, fd: int, data: Union[bytes, Text]) -> None: ...
    def pipe_connection_lost(self, fd: int, exc: Optional[Exception]) -> None: ...
    def process_exited(self) -> None: ...


class Process:
    stdin: Optional[streams.StreamWriter]
    stdout: Optional[streams.StreamReader]
    stderr: Optional[streams.StreamReader]
    pid: int
    def __init__(self,
                 transport: transports.BaseTransport,
                 protocol: protocols.BaseProtocol,
                 loop: events.AbstractEventLoop) -> None: ...
    @property
    def returncode(self) -> int: ...
    @coroutine
    def wait(self) -> Generator[Any, None, int]: ...
    def send_signal(self, signal: int) -> None: ...
    def terminate(self) -> None: ...
    def kill(self) -> None: ...
    @coroutine
    def communicate(self, input: Optional[bytes] = ...) -> Generator[Any, None, Tuple[bytes, bytes]]: ...


@coroutine
def create_subprocess_shell(
    *Args: Union[str, bytes],  # Union used instead of AnyStr due to mypy issue  #1236
    stdin: Union[int, IO[Any], None] = ...,
    stdout: Union[int, IO[Any], None] = ...,
    stderr: Union[int, IO[Any], None] = ...,
    loop: events.AbstractEventLoop = ...,
    limit: int = ...,
    **kwds: Any
) -> Generator[Any, None, Process]: ...

@coroutine
def create_subprocess_exec(
    program: Union[str, bytes],  # Union used instead of AnyStr due to mypy issue  #1236
    *args: Any,
    stdin: Union[int, IO[Any], None] = ...,
    stdout: Union[int, IO[Any], None] = ...,
    stderr: Union[int, IO[Any], None] = ...,
    loop: events.AbstractEventLoop = ...,
    limit: int = ...,
    **kwds: Any
) -> Generator[Any, None, Process]: ...
