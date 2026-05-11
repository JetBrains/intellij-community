import abc
from _typeshed import Incomplete
from collections.abc import Callable
from logging import Logger
from typing_extensions import Self

from ..adapters.utils.nbio_interface import AbstractIOServices, AbstractStreamProtocol
from ..connection import Connection, Parameters

LOGGER: Logger

class BaseConnection(Connection, metaclass=abc.ABCMeta):
    def __init__(
        self,
        parameters: Parameters | None,
        on_open_callback: Callable[[Self], object] | None,
        on_open_error_callback: Callable[[Self, BaseException], object] | None,
        on_close_callback: Callable[[Self, BaseException], object] | None,
        nbio: AbstractIOServices,
        internal_connection_workflow: bool = True,
    ) -> None: ...
    @classmethod
    @abc.abstractmethod
    def create_connection(cls, connection_configs, on_done, custom_ioloop=None, workflow=None): ...
    @property
    def ioloop(self): ...

class _StreamingProtocolShim(AbstractStreamProtocol):
    connection_made: Incomplete
    connection_lost: Incomplete
    eof_received: Incomplete
    data_received: Incomplete
    conn: Incomplete
    def __init__(self, conn) -> None: ...
    def __getattr__(self, attr: str): ...
