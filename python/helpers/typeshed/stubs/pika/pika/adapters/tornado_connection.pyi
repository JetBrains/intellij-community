from _typeshed import Incomplete
from collections.abc import Callable, Sequence
from logging import Logger
from typing import TypeAlias
from typing_extensions import Self

from pika.adapters.base_connection import BaseConnection
from pika.adapters.utils.connection_workflow import AbstractAMQPConnectionWorkflow, AMQPConnectorException
from pika.adapters.utils.nbio_interface import AbstractIOServices
from pika.connection import Parameters

_IOLoop: TypeAlias = Incomplete  # actual type is tornado.ioloop.IOLoop

LOGGER: Logger

class TornadoConnection(BaseConnection[_IOLoop]):
    def __init__(
        self,
        parameters: Parameters | None = None,
        on_open_callback: Callable[[Self], object] | None = None,
        on_open_error_callback: Callable[[Self, BaseException], object] | None = None,
        on_close_callback: Callable[[Self, BaseException], object] | None = None,
        custom_ioloop: _IOLoop | AbstractIOServices | None = None,
        internal_connection_workflow: bool = True,
    ) -> None: ...
    @classmethod
    def create_connection(
        cls,
        connection_configs: Sequence[Parameters],
        on_done: Callable[[Self | AMQPConnectorException], object],
        custom_ioloop: _IOLoop | None = None,
        workflow: AbstractAMQPConnectionWorkflow | None = None,
    ) -> AbstractAMQPConnectionWorkflow: ...
