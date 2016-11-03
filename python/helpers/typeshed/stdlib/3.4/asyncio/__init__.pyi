"""The asyncio package, tracking PEP 3156."""

import sys
from typing import Type

from asyncio.coroutines import (
    coroutine as coroutine,
    iscoroutinefunction as iscoroutinefunction,
    iscoroutine as iscoroutine,
)
from asyncio.protocols import (
    BaseProtocol as BaseProtocol,
    Protocol as Protocol,
    DatagramProtocol as DatagramProtocol,
    SubprocessProtocol as SubprocessProtocol,
)
from asyncio.streams import (
    StreamReader as StreamReader,
    StreamWriter as StreamWriter,
    StreamReaderProtocol as StreamReaderProtocol,
    open_connection as open_connection,
    start_server as start_server,
    IncompleteReadError as IncompleteReadError,
    LimitOverrunError as LimitOverrunError,
)
from asyncio.subprocess import (
    create_subprocess_exec as create_subprocess_exec,
    create_subprocess_shell as create_subprocess_shell,
)
from asyncio.transports import (
    BaseTransport as BaseTransport,
    ReadTransport as ReadTransport,
    WriteTransport as WriteTransport,
    Transport as Transport,
    DatagramTransport as DatagramTransport,
    SubprocessTransport as SubprocessTransport,
)
from asyncio.futures import (
    Future as Future,
)
from asyncio.tasks import (
    sleep as sleep,
    Task as Task,
    FIRST_COMPLETED as FIRST_COMPLETED,
    FIRST_EXCEPTION as FIRST_EXCEPTION,
    ALL_COMPLETED as ALL_COMPLETED,
    wait as wait,
    wait_for as wait_for,
)
from asyncio.events import (
    AbstractEventLoopPolicy as AbstractEventLoopPolicy,
    AbstractEventLoop as AbstractEventLoop,
    AbstractServer as AbstractServer,
    Handle as Handle,
    get_event_loop_policy as get_event_loop_policy,
    set_event_loop_policy as set_event_loop_policy,
    get_event_loop as get_event_loop,
    set_event_loop as set_event_loop,
    new_event_loop as new_event_loop,
    get_child_watcher as get_child_watcher,
    set_child_watcher as set_child_watcher,
)
from asyncio.queues import (
    Queue as Queue,
    PriorityQueue as PriorityQueue,
    LifoQueue as LifoQueue,
    JoinableQueue as JoinableQueue,
    QueueFull as QueueFull,
    QueueEmpty as QueueEmpty,
)
from asyncio.locks import (
    Lock as Lock,
    Event as Event,
    Condition as Condition,
    Semaphore as Semaphore,
    BoundedSemaphore as BoundedSemaphore,
)

# TODO: It should be possible to instantiate these classes, but mypy
# currently disallows this.
# See https://github.com/python/mypy/issues/1843
SelectorEventLoop = ...  # type: Type[AbstractEventLoop]
if sys.platform == 'win32':
    ProactorEventLoop = ...  # type: Type[AbstractEventLoop]
DefaultEventLoopPolicy = ...  # type: Type[AbstractEventLoopPolicy]

# TODO: AbstractChildWatcher (UNIX only)

__all__ = ... # type: str
