from typing import Any, Awaitable, TypeVar, List, Callable, Tuple, Union, Dict, Generator, overload
from abc import ABCMeta, abstractmethod
from asyncio.futures import Future
from asyncio.coroutines import coroutine

__all__ = ... # type: str

_T = TypeVar('_T')

PIPE = ...  # type: Any  # from subprocess.PIPE

AF_UNSPEC = 0     # from socket
AI_PASSIVE = 0

class Handle:
    __slots__ = ... # type: List[str]
    _cancelled = False
    _args = ... # type: List[Any]
    def __init__(self, callback: Callable[[],Any], args: List[Any],
        loop: AbstractEventLoop) -> None: ...
    def __repr__(self) -> str: ...
    def cancel(self) -> None: ...
    def _run(self) -> None: ...

class AbstractServer:
    def close(self) -> None: ...
    @coroutine
    def wait_closed(self) -> Generator[Any, Any, None]: ...

class AbstractEventLoop(metaclass=ABCMeta):
    @abstractmethod
    def run_forever(self) -> None: ...

    # Can't use a union, see mypy issue #1873.
    @overload
    @abstractmethod
    def run_until_complete(self, future: Generator[Any, Any, _T]) -> _T: ...
    @overload
    @abstractmethod
    def run_until_complete(self, future: Awaitable[_T]) -> _T: ...

    @abstractmethod
    def stop(self) -> None: ...
    @abstractmethod
    def is_running(self) -> bool: ...
    @abstractmethod
    def close(self) -> None: ...
    # Methods scheduling callbacks.  All these return Handles.
    @abstractmethod
    def call_soon(self, callback: Callable[[],Any], *args: Any) -> Handle: ...
    @abstractmethod
    def call_later(self, delay: Union[int, float], callback: Callable[[],Any], *args: Any) -> Handle: ...
    @abstractmethod
    def call_at(self, when: float, callback: Callable[[],Any], *args: Any) -> Handle: ...
    @abstractmethod
    def time(self) -> float: ...
    # Methods for interacting with threads
    @abstractmethod
    def call_soon_threadsafe(self, callback: Callable[[],Any], *args: Any) -> Handle: ...
    @abstractmethod
    def run_in_executor(self, executor: Any,
        callback: Callable[[],Any], *args: Any) -> Future[Any]: ...
    @abstractmethod
    def set_default_executor(self, executor: Any) -> None: ...
    # Network I/O methods returning Futures.
    @abstractmethod
    def getaddrinfo(self, host: str, port: int, *,
        family: int = ..., type: int = ..., proto: int = ..., flags: int = ...) -> Future[List[Tuple[int, int, int, str, tuple]]]: ...
    @abstractmethod
    def getnameinfo(self, sockaddr: tuple, flags: int = ...) -> Future[Tuple[str, int]]: ...
    @abstractmethod
    def create_connection(self, protocol_factory: Any, host: str = ..., port: int = ..., *,
                          ssl: Any = ..., family: int = ..., proto: int = ..., flags: int = ..., sock: Any = ...,
                          local_addr: str = ..., server_hostname: str = ...) -> tuple: ...
                          # ?? check Any
                          # return (Transport, Protocol)
    @abstractmethod
    def create_server(self, protocol_factory: Any, host: str = ..., port: int = ..., *,
                      family: int = ..., flags: int = ...,
                      sock: Any = ..., backlog: int = ..., ssl: Any = ..., reuse_address: Any = ...) -> Any: ...
                    # ?? check Any
                    # return Server
    @abstractmethod
    def create_unix_connection(self, protocol_factory: Any, path: str, *,
                               ssl: Any = ..., sock: Any = ...,
                               server_hostname: str = ...) -> tuple: ...
                    # ?? check Any
                    # return tuple(Transport, Protocol)
    @abstractmethod
    def create_unix_server(self, protocol_factory: Any, path: str, *,
                           sock: Any = ..., backlog: int = ..., ssl: Any = ...) -> Any: ...
                    # ?? check Any
                    # return Server
    @abstractmethod
    def create_datagram_endpoint(self, protocol_factory: Any,
                                 local_addr: str = ..., remote_addr: str = ..., *,
                                 family: int = ..., proto: int = ..., flags: int = ...) -> tuple: ...
                    #?? check Any
                    # return (Transport, Protocol)
    # Pipes and subprocesses.
    @abstractmethod
    def connect_read_pipe(self, protocol_factory: Any, pipe: Any) -> tuple: ...
                    #?? check Any
                    # return (Transport, Protocol)
    @abstractmethod
    def connect_write_pipe(self, protocol_factory: Any, pipe: Any) -> tuple: ...
                    #?? check Any
                    # return (Transport, Protocol)
    @abstractmethod
    def subprocess_shell(self, protocol_factory: Any, cmd: Union[bytes, str], *, stdin: Any = ...,
                         stdout: Any = ..., stderr: Any = ...,
                         **kwargs: Any) -> tuple: ...
                    #?? check Any
                    # return (Transport, Protocol)
    @abstractmethod
    def subprocess_exec(self, protocol_factory: Any, *args: List[Any], stdin: Any = ...,
                        stdout: Any = ..., stderr: Any = ...,
                        **kwargs: Any) -> tuple: ...
                    #?? check Any
                    # return (Transport, Protocol)
    @abstractmethod
    def add_reader(self, fd: int, callback: Callable[[],Any], *args: List[Any]) -> None: ...
    @abstractmethod
    def remove_reader(self, fd: int) -> None: ...
    @abstractmethod
    def add_writer(self, fd: int, callback: Callable[[],Any], *args: List[Any]) -> None: ...
    @abstractmethod
    def remove_writer(self, fd: int) -> None: ...
    # Completion based I/O methods returning Futures.
    @abstractmethod
    def sock_recv(self, sock: Any, nbytes: int) -> Any: ... #TODO
    @abstractmethod
    def sock_sendall(self, sock: Any, data: bytes) -> None: ... #TODO
    @abstractmethod
    def sock_connect(self, sock: Any, address: str) -> Any: ... #TODO
    @abstractmethod
    def sock_accept(self, sock: Any) -> Any: ...
    # Signal handling.
    @abstractmethod
    def add_signal_handler(self, sig: int, callback: Callable[[],Any], *args: List[Any]) -> None: ...
    @abstractmethod
    def remove_signal_handler(self, sig: int) -> None: ...
    # Error handlers.
    @abstractmethod
    def set_exception_handler(self, handler: Callable[[], Any]) -> None: ...
    @abstractmethod
    def default_exception_handler(self, context: Any) -> None: ...
    @abstractmethod
    def call_exception_handler(self, context: Any) -> None: ...
    # Debug flag management.
    @abstractmethod
    def get_debug(self) -> bool: ...
    @abstractmethod
    def set_debug(self, enabled: bool) -> None: ...

class AbstractEventLoopPolicy(metaclass=ABCMeta):
    @abstractmethod
    def get_event_loop(self) -> AbstractEventLoop: ...
    @abstractmethod
    def set_event_loop(self, loop: AbstractEventLoop): ...
    @abstractmethod
    def new_event_loop(self) -> AbstractEventLoop: ...
    # Child processes handling (Unix only).
    @abstractmethod
    def get_child_watcher(self) -> Any: ...  # TODO: unix_events.AbstractChildWatcher
    @abstractmethod
    def set_child_watcher(self, watcher: Any) -> None: ... # TODO: unix_events.AbstractChildWatcher

class BaseDefaultEventLoopPolicy(AbstractEventLoopPolicy):
    def __init__(self) -> None: ...
    def get_event_loop(self) -> AbstractEventLoop: ...
    def set_event_loop(self, loop: AbstractEventLoop): ...
    def new_event_loop(self) -> AbstractEventLoop: ...

def get_event_loop_policy() -> AbstractEventLoopPolicy: ...
def set_event_loop_policy(policy: AbstractEventLoopPolicy) -> None: ...

def get_event_loop() -> AbstractEventLoop: ...
def set_event_loop(loop: AbstractEventLoop) -> None: ...
def new_event_loop() -> AbstractEventLoop: ...

def get_child_watcher() -> Any: ...  # TODO: unix_events.AbstractChildWatcher
def set_child_watcher(watcher: Any) -> None: ... # TODO: unix_events.AbstractChildWatcher
