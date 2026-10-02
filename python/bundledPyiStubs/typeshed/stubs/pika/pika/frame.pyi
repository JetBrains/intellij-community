from abc import abstractmethod
from logging import Logger
from typing import Generic, TypeVar

from . import amqp_object
from .spec import BasicProperties

_M = TypeVar("_M", bound=amqp_object.Method)

LOGGER: Logger

class Frame(amqp_object.AMQPObject):
    frame_type: int
    channel_number: int
    def __init__(self, frame_type: int, channel_number: int) -> None: ...
    @abstractmethod
    def marshal(self) -> bytes: ...

class Method(Frame, Generic[_M]):
    method: _M
    def __init__(self, channel_number: int, method: _M) -> None: ...
    def marshal(self) -> bytes: ...

class Header(Frame):
    body_size: int
    properties: BasicProperties
    def __init__(self, channel_number: int, body_size: int, props: BasicProperties) -> None: ...
    def marshal(self) -> bytes: ...

class Body(Frame):
    fragment: bytes
    def __init__(self, channel_number: int, fragment: bytes) -> None: ...
    def marshal(self) -> bytes: ...

class Heartbeat(Frame):
    def __init__(self) -> None: ...
    def marshal(self) -> bytes: ...

class ProtocolHeader(amqp_object.AMQPObject):
    frame_type: int
    major: int
    minor: int
    revision: int
    def __init__(self, major: int | None = None, minor: int | None = None, revision: int | None = None) -> None: ...
    def marshal(self) -> bytes: ...

def decode_frame(data_in: bytes) -> tuple[int, Frame | ProtocolHeader | None]: ...
