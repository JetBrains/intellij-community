# Stubs for logging.handlers (Python 2.4)

import datetime
from logging import Handler, FileHandler, LogRecord
from socket import SocketType
import ssl
import sys
from typing import Any, Callable, Dict, List, Optional, Tuple, Union, overload
if sys.version_info >= (3,):
    from queue import Queue
else:
    from Queue import Queue
# TODO update socket stubs to add SocketKind
_SocketKind = int


class WatchedFileHandler(Handler):
    @overload
    def __init__(self, filename: str) -> None: ...
    @overload
    def __init__(self, filename: str, mode: str) -> None: ...
    @overload
    def __init__(self, filename: str, mode: str,
                 encoding: Optional[str]) -> None: ...
    @overload
    def __init__(self, filename: str, mode: str, encoding: Optional[str],
                 delay: bool) -> None: ...


if sys.version_info >= (3,):
    class BaseRotatingHandler(FileHandler):
        namer = ...  # type: Optional[Callable[[str], str]]
        rotator = ...  # type: Optional[Callable[[str, str], None]]
        def __init__(self, filename: str, mode: str,
                     encoding: Optional[str] = ...,
                     delay: bool = ...) -> None: ...
        def rotation_filename(self, default_name: str) -> None: ...
        def rotate(self, source: str, dest: str) -> None: ...


if sys.version_info >= (3,):
    class RotatingFileHandler(BaseRotatingHandler):
        def __init__(self, filename: str, mode: str = ..., maxBytes: int = ...,
                     backupCount: int = ..., encoding: Optional[str] = ...,
                     delay: bool = ...) -> None: ...
        def doRollover(self) -> None: ...
else:
    class RotatingFileHandler(Handler):
        def __init__(self, filename: str, mode: str = ..., maxBytes: int = ...,
                     backupCount: int = ..., encoding: Optional[str] = ...,
                     delay: bool = ...) -> None: ...
        def doRollover(self) -> None: ...


if sys.version_info >= (3,):
    class TimedRotatingFileHandler(BaseRotatingHandler):
        if sys.version_info >= (3, 4):
            def __init__(self, filename: str, when: str = ...,
                         interval: int = ...,
                         backupCount: int = ..., encoding: Optional[str] = ...,
                         delay: bool = ..., utc: bool = ...,
                         atTime: Optional[datetime.datetime] = ...) -> None: ...
        else:
            def __init__(self,
                         filename: str, when: str = ..., interval: int = ...,
                         backupCount: int = ..., encoding: Optional[str] = ...,
                         delay: bool = ..., utc: bool = ...) -> None: ...
        def doRollover(self) -> None: ...
else:
    class TimedRotatingFileHandler:
        def __init__(self,
                     filename: str, when: str = ..., interval: int = ...,
                     backupCount: int = ..., encoding: Optional[str] = ...,
                     delay: bool = ..., utc: bool = ...) -> None: ...
        def doRollover(self) -> None: ...


class SocketHandler(Handler):
    retryStart = ...  # type: float
    retryFactor = ...  # type: float
    retryMax = ...  # type: float
    if sys.version_info >= (3, 4):
        def __init__(self, host: str, port: Optional[int]) -> None: ...
    else:
        def __init__(self, host: str, port: int) -> None: ...
    def makeSocket(self) -> SocketType: ...
    def makePickle(self, record: LogRecord) -> bytes: ...
    def send(self, packet: bytes) -> None: ...
    def createSocket(self) -> None: ...


class DatagramHandler(SocketHandler): ...


class SysLogHandler(Handler):
    LOG_ALERT = ...  # type: int
    LOG_CRIT = ...  # type: int
    LOG_DEBUG = ...  # type: int
    LOG_EMERG = ...  # type: int
    LOG_ERR = ...  # type: int
    LOG_INFO = ...  # type: int
    LOG_NOTICE = ...  # type: int
    LOG_WARNING = ...  # type: int
    LOG_AUTH = ...  # type: int
    LOG_AUTHPRIV = ...  # type: int
    LOG_CRON = ...  # type: int
    LOG_DAEMON = ...  # type: int
    LOG_FTP = ...  # type: int
    LOG_KERN = ...  # type: int
    LOG_LPR = ...  # type: int
    LOG_MAIL = ...  # type: int
    LOG_NEWS = ...  # type: int
    LOG_SYSLOG = ...  # type: int
    LOG_USER = ...  # type: int
    LOG_UUCP = ...  # type: int
    LOG_LOCAL0 = ...  # type: int
    LOG_LOCAL1 = ...  # type: int
    LOG_LOCAL2 = ...  # type: int
    LOG_LOCAL3 = ...  # type: int
    LOG_LOCAL4 = ...  # type: int
    LOG_LOCAL5 = ...  # type: int
    LOG_LOCAL6 = ...  # type: int
    LOG_LOCAL7 = ...  # type: int
    def __init__(self, address: Union[Tuple[str, int], str] = ...,
            facility: int = ..., socktype: _SocketKind = ...) -> None: ...
    def encodePriority(self, facility: Union[int, str],
                       priority: Union[int, str]) -> int: ...
    def mapPriority(self, levelName: int) -> str: ...


class NTEventLogHandler(Handler):
    def __init__(self, appname: str, dllname: str = ...,
                 logtype: str = ...) -> None: ...
    def getEventCategory(self, record: LogRecord) -> int: ...
    # TODO correct return value?
    def getEventType(self, record: LogRecord) -> int: ...
    def getMessageID(self, record: LogRecord) -> int: ...


class SMTPHandler(Handler):
    # TODO `secure` can also be an empty tuple
    if sys.version_info >= (3,):
        def __init__(self, mailhost: Union[str, Tuple[str, int]], fromaddr: str,
                     toaddrs: List[str], subject: str,
                     credentials: Optional[Tuple[str, str]] = ...,
                     secure: Union[Tuple[str], Tuple[str, str], None] =...,
                     timeout: float = ...) -> None: ...
    else:
        def __init__(self,
                     mailhost: Union[str, Tuple[str, int]], fromaddr: str,
                     toaddrs: List[str], subject: str,
                     credentials: Optional[Tuple[str, str]] = ...,
                     secure: Union[Tuple[str], Tuple[str, str], None] =...) -> None: ...
    def getSubject(self, record: LogRecord) -> str: ...


class BufferingHandler(Handler):
    def __init__(self, capacity: int) -> None: ...
    def shouldFlush(self, record: LogRecord) -> bool: ...

class MemoryHandler(BufferingHandler):
    def __init__(self, capacity: int, flushLevel: int = ...,
                 target: Optional[Handler] =...) -> None: ...
    def setTarget(self, target: Handler) -> None: ...


class HTTPHandler(Handler):
    if sys.version_info >= (3, 5):
        def __init__(self, host: str, url: str, method: str = ...,
                     secure: bool = ...,
                     credentials: Optional[Tuple[str, str]] = ...,
                     context: Optional[ssl.SSLContext] = ...) -> None: ...
    elif sys.version_info >= (3,):
        def __init__(self,
                     host: str, url: str, method: str = ..., secure: bool = ...,
                     credentials: Optional[Tuple[str, str]] = ...) -> None: ...
    else:
        def __init__(self,
                     host: str, url: str, method: str = ...) -> None: ...
    def mapLogRecord(self, record: LogRecord) -> Dict[str, Any]: ...


if sys.version_info >= (3,):
    class QueueHandler(Handler):
        def __init__(self, queue: Queue) -> None: ...
        def prepare(self, record: LogRecord) -> Any: ...
        def enqueue(self, record: LogRecord) -> None: ...

    class QueueListener:
        if sys.version_info >= (3, 5):
            def __init__(self, queue: Queue, *handlers: Handler,
                         respect_handler_level: bool = ...) -> None: ...
        else:
            def __init__(self,
                         queue: Queue, *handlers: Handler) -> None: ...
        def dequeue(self, block: bool) -> LogRecord: ...
        def prepare(self, record: LogRecord) -> Any: ...
        def start(self) -> None: ...
        def stop(self) -> None: ...
        def enqueue_sentinel(self) -> None: ...
