from asyncio import StreamReader, StreamWriter
from socket import socket
from typing import Any, ClassVar, Literal, TypeAlias, TypedDict, type_check_only

@type_check_only
class _CtlRequest(TypedDict):
    id: int
    command: str
    args: list[str]

@type_check_only
class _CtlSuccessResponse(TypedDict):
    id: int
    status: Literal["ok"]
    data: dict[str, Any]

@type_check_only
class _CtlErrorResponse(TypedDict):
    id: int
    status: Literal["error"]
    error: str

_CtlResponse: TypeAlias = _CtlSuccessResponse | _CtlErrorResponse
_CtlMessage: TypeAlias = _CtlRequest | _CtlResponse

class ProtocolError(Exception): ...

class ControlProtocol:
    MAX_MESSAGE_SIZE: ClassVar[int]
    @staticmethod
    def encode_message(data: _CtlMessage) -> bytes: ...
    @staticmethod
    def decode_message(data: bytes) -> _CtlMessage: ...
    @staticmethod
    def read_message(sock: socket) -> _CtlMessage: ...
    @staticmethod
    def write_message(sock: socket, data: _CtlMessage) -> None: ...
    @staticmethod
    async def read_message_async(reader: StreamReader) -> _CtlMessage: ...
    @staticmethod
    async def write_message_async(writer: StreamWriter, data: _CtlMessage) -> None: ...

def make_request(request_id: int, command: str, args: list[str] | None = None) -> _CtlRequest: ...
def make_response(request_id: int, data: dict[str, Any] | None = None) -> _CtlSuccessResponse: ...
def make_error_response(request_id: int, error: str) -> _CtlErrorResponse: ...
