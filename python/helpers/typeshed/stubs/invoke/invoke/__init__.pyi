from typing import Any

from .collection import Collection as Collection
from .config import Config as Config
from .context import Context as Context, MockContext as MockContext
from .exceptions import (
    AmbiguousEnvVar as AmbiguousEnvVar,
    AuthFailure as AuthFailure,
    CollectionNotFound as CollectionNotFound,
    CommandTimedOut as CommandTimedOut,
    Exit as Exit,
    Failure as Failure,
    ParseError as ParseError,
    PlatformError as PlatformError,
    ResponseNotAccepted as ResponseNotAccepted,
    SubprocessPipeError as SubprocessPipeError,
    ThreadException as ThreadException,
    UncastableEnvVar as UncastableEnvVar,
    UnexpectedExit as UnexpectedExit,
    UnknownFileType as UnknownFileType,
    UnpicklableConfigMember as UnpicklableConfigMember,
    WatcherError as WatcherError,
)
from .executor import Executor as Executor
from .loader import FilesystemLoader as FilesystemLoader
from .parser import Argument as Argument, Parser as Parser, ParserContext as ParserContext, ParseResult as ParseResult
from .program import Program as Program
from .runners import Local as Local, Promise as Promise, Result as Result, Runner as Runner
from .tasks import Call as Call, Task as Task, call as call, task as task
from .terminals import pty_size as pty_size
from .watchers import FailingResponder as FailingResponder, Responder as Responder, StreamWatcher as StreamWatcher

__version_info__: tuple[int, int, int]
__version__: str

def run(command: str, **kwargs: Any) -> Result: ...
def sudo(command: str, **kwargs: Any) -> Result: ...
