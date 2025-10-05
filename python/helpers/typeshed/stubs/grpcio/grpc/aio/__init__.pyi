import abc
import asyncio
from _typeshed import Incomplete
from collections.abc import AsyncIterable, AsyncIterator, Awaitable, Callable, Generator, Iterable, Iterator, Mapping, Sequence
from concurrent import futures
from types import TracebackType
from typing import Any, Generic, NoReturn, TypeVar, overload, type_check_only
from typing_extensions import Self, TypeAlias

from grpc import (
    CallCredentials,
    ChannelConnectivity,
    ChannelCredentials,
    Compression,
    GenericRpcHandler,
    HandlerCallDetails,
    RpcError,
    RpcMethodHandler,
    ServerCredentials,
    StatusCode,
    _Options,
)

_TRequest = TypeVar("_TRequest")
_TResponse = TypeVar("_TResponse")

# Exceptions:

class BaseError(Exception): ...
class UsageError(BaseError): ...
class AbortError(BaseError): ...
class InternalError(BaseError): ...

class AioRpcError(RpcError):
    def __init__(
        self,
        code: StatusCode,
        initial_metadata: Metadata,
        trailing_metadata: Metadata,
        details: str | None = None,
        debug_error_string: str | None = None,
    ) -> None: ...
    def debug_error_string(self) -> str: ...
    def initial_metadata(self) -> Metadata: ...

# Create Client:

class ClientInterceptor(metaclass=abc.ABCMeta): ...

def insecure_channel(
    target: str,
    options: _Options | None = None,
    compression: Compression | None = None,
    interceptors: Sequence[ClientInterceptor] | None = None,
) -> Channel: ...
def secure_channel(
    target: str,
    credentials: ChannelCredentials,
    options: _Options | None = None,
    compression: Compression | None = None,
    interceptors: Sequence[ClientInterceptor] | None = None,
) -> Channel: ...

# Create Server:

def server(
    migration_thread_pool: futures.Executor | None = None,
    handlers: Sequence[GenericRpcHandler[Any, Any]] | None = None,
    interceptors: Sequence[ServerInterceptor[Any, Any]] | None = None,
    options: _Options | None = None,
    maximum_concurrent_rpcs: int | None = None,
    compression: Compression | None = None,
) -> Server: ...

# Channel Object:

_Serializer: TypeAlias = Callable[[_T], bytes]
_Deserializer: TypeAlias = Callable[[bytes], _T]

class Channel(abc.ABC):
    @abc.abstractmethod
    async def close(self, grace: float | None = None) -> None: ...
    @abc.abstractmethod
    def get_state(self, try_to_connect: bool = False) -> ChannelConnectivity: ...
    @abc.abstractmethod
    async def wait_for_state_change(self, last_observed_state: ChannelConnectivity) -> None: ...
    @abc.abstractmethod
    def stream_stream(
        self,
        method: str,
        request_serializer: _Serializer[_TRequest] | None = None,
        response_deserializer: _Deserializer[_TResponse] | None = None,
    ) -> StreamStreamMultiCallable[_TRequest, _TResponse]: ...
    @abc.abstractmethod
    def stream_unary(
        self,
        method: str,
        request_serializer: _Serializer[_TRequest] | None = None,
        response_deserializer: _Deserializer[_TResponse] | None = None,
    ) -> StreamUnaryMultiCallable[_TRequest, _TResponse]: ...
    @abc.abstractmethod
    def unary_stream(
        self,
        method: str,
        request_serializer: _Serializer[_TRequest] | None = None,
        response_deserializer: _Deserializer[_TResponse] | None = None,
    ) -> UnaryStreamMultiCallable[_TRequest, _TResponse]: ...
    @abc.abstractmethod
    def unary_unary(
        self,
        method: str,
        request_serializer: _Serializer[_TRequest] | None = None,
        response_deserializer: _Deserializer[_TResponse] | None = None,
    ) -> UnaryUnaryMultiCallable[_TRequest, _TResponse]: ...
    @abc.abstractmethod
    async def __aenter__(self) -> Self: ...
    @abc.abstractmethod
    async def __aexit__(
        self, exc_type: type[BaseException] | None, exc_val: BaseException | None, exc_tb: TracebackType | None
    ) -> bool | None: ...
    @abc.abstractmethod
    async def channel_ready(self) -> None: ...

# Server Object:

class Server(metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def add_generic_rpc_handlers(self, generic_rpc_handlers: Iterable[GenericRpcHandler[Any, Any]]) -> None: ...

    # Returns an integer port on which server will accept RPC requests.
    @abc.abstractmethod
    def add_insecure_port(self, address: str) -> int: ...

    # Returns an integer port on which server will accept RPC requests.
    @abc.abstractmethod
    def add_secure_port(self, address: str, server_credentials: ServerCredentials) -> int: ...
    @abc.abstractmethod
    async def start(self) -> None: ...

    # Grace period is in seconds.
    @abc.abstractmethod
    async def stop(self, grace: float | None) -> None: ...

    # Returns a bool indicates if the operation times out. Timeout is in seconds.
    @abc.abstractmethod
    async def wait_for_termination(self, timeout: float | None = None) -> bool: ...

# Client-Side Context:

_DoneCallbackType: TypeAlias = Callable[[Any], None]
_EOFType: TypeAlias = object

class RpcContext(metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def cancelled(self) -> bool: ...
    @abc.abstractmethod
    def done(self) -> bool: ...
    @abc.abstractmethod
    def time_remaining(self) -> float | None: ...
    @abc.abstractmethod
    def cancel(self) -> bool: ...
    @abc.abstractmethod
    def add_done_callback(self, callback: _DoneCallbackType) -> None: ...

class Call(RpcContext, metaclass=abc.ABCMeta):
    @abc.abstractmethod
    async def initial_metadata(self) -> Metadata: ...
    @abc.abstractmethod
    async def trailing_metadata(self) -> Metadata: ...
    @abc.abstractmethod
    async def code(self) -> StatusCode: ...
    @abc.abstractmethod
    async def details(self) -> str: ...
    @abc.abstractmethod
    async def wait_for_connection(self) -> None: ...

class UnaryUnaryCall(Call, Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def __await__(self) -> Generator[None, None, _TResponse]: ...

class UnaryStreamCall(Call, Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def __aiter__(self) -> AsyncIterator[_TResponse]: ...
    @abc.abstractmethod
    async def read(self) -> _EOFType | _TResponse: ...

class StreamUnaryCall(Call, Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    async def write(self, request: _TRequest) -> None: ...
    @abc.abstractmethod
    async def done_writing(self) -> None: ...
    @abc.abstractmethod
    def __await__(self) -> Generator[None, None, _TResponse]: ...

class StreamStreamCall(Call, Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def __aiter__(self) -> AsyncIterator[_TResponse]: ...
    @abc.abstractmethod
    async def read(self) -> _EOFType | _TResponse: ...
    @abc.abstractmethod
    async def write(self, request: _TRequest) -> None: ...
    @abc.abstractmethod
    async def done_writing(self) -> None: ...

# Service-Side Context:

@type_check_only
class _DoneCallback(Generic[_TRequest, _TResponse]):
    def __call__(self, ctx: ServicerContext[_TRequest, _TResponse]) -> None: ...

class ServicerContext(Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    async def abort(self, code: StatusCode, details: str = "", trailing_metadata: _MetadataType = ()) -> NoReturn: ...
    @abc.abstractmethod
    async def read(self) -> _TRequest: ...
    @abc.abstractmethod
    async def write(self, message: _TResponse) -> None: ...
    @abc.abstractmethod
    async def send_initial_metadata(self, initial_metadata: _MetadataType) -> None: ...
    def add_done_callback(self, callback: _DoneCallback[_TRequest, _TResponse]) -> None: ...
    @abc.abstractmethod
    def set_trailing_metadata(self, trailing_metadata: _MetadataType) -> None: ...
    @abc.abstractmethod
    def invocation_metadata(self) -> Metadata | None: ...
    @abc.abstractmethod
    def set_code(self, code: StatusCode) -> None: ...
    @abc.abstractmethod
    def set_details(self, details: str) -> None: ...
    @abc.abstractmethod
    def set_compression(self, compression: Compression) -> None: ...
    @abc.abstractmethod
    def disable_next_message_compression(self) -> None: ...
    @abc.abstractmethod
    def peer(self) -> str: ...
    @abc.abstractmethod
    def peer_identities(self) -> Iterable[bytes] | None: ...
    @abc.abstractmethod
    def peer_identity_key(self) -> str | None: ...
    @abc.abstractmethod
    def auth_context(self) -> Mapping[str, Iterable[bytes]]: ...
    def time_remaining(self) -> float: ...
    def trailing_metadata(self) -> Metadata: ...
    def code(self) -> StatusCode: ...
    def details(self) -> str: ...
    def cancelled(self) -> bool: ...
    def done(self) -> bool: ...

# Client-Side Interceptor:

class ClientCallDetails(abc.ABC):
    def __init__(
        self,
        method: str,
        timeout: float | None,
        metadata: Metadata | None,
        credentials: CallCredentials | None,
        wait_for_ready: bool | None,
    ) -> None: ...

    method: str
    timeout: float | None
    metadata: Metadata | None
    credentials: CallCredentials | None

    # "This is an EXPERIMENTAL argument. An optional flag t enable wait for ready mechanism."
    wait_for_ready: bool | None

    # As at 1.53.0, this is not supported in aio:
    # compression: Compression | None

@type_check_only
class _InterceptedCall(Generic[_TRequest, _TResponse]):
    def __init__(self, interceptors_task: asyncio.Task[Any]) -> None: ...
    def __del__(self) -> None: ...
    def cancel(self) -> bool: ...
    def cancelled(self) -> bool: ...
    def done(self) -> bool: ...
    def add_done_callback(self, callback: _DoneCallback[_TRequest, _TResponse]) -> None: ...
    def time_remaining(self) -> float | None: ...
    async def initial_metadata(self) -> Metadata | None: ...
    async def trailing_metadata(self) -> Metadata | None: ...
    async def code(self) -> StatusCode: ...
    async def details(self) -> str: ...
    async def debug_error_string(self) -> str | None: ...
    async def wait_for_connection(self) -> None: ...

class InterceptedUnaryUnaryCall(_InterceptedCall[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    def __await__(self) -> Generator[Incomplete, None, _TResponse]: ...
    def __init__(
        self,
        interceptors: Sequence[UnaryUnaryClientInterceptor[_TRequest, _TResponse]],
        request: _TRequest,
        timeout: float | None,
        metadata: Metadata,
        credentials: CallCredentials | None,
        wait_for_ready: bool | None,
        channel: Channel,
        method: bytes,
        request_serializer: _Serializer[_TRequest],
        response_deserializer: _Deserializer[_TResponse],
        loop: asyncio.AbstractEventLoop,
    ) -> None: ...

    # pylint: disable=too-many-arguments
    async def _invoke(
        self,
        interceptors: Sequence[UnaryUnaryClientInterceptor[_TRequest, _TResponse]],
        method: bytes,
        timeout: float | None,
        metadata: Metadata | None,
        credentials: CallCredentials | None,
        wait_for_ready: bool | None,
        request: _TRequest,
        request_serializer: _Serializer[_TRequest],
        response_deserializer: _Deserializer[_TResponse],
    ) -> UnaryUnaryCall[_TRequest, _TResponse]: ...
    def time_remaining(self) -> float | None: ...

class UnaryUnaryClientInterceptor(Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    async def intercept_unary_unary(
        self,
        # XXX: See equivalent function in grpc types for notes about continuation:
        continuation: Callable[[ClientCallDetails, _TRequest], UnaryUnaryCall[_TRequest, _TResponse]],
        client_call_details: ClientCallDetails,
        request: _TRequest,
    ) -> _TResponse: ...

class UnaryStreamClientInterceptor(Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    async def intercept_unary_stream(
        self,
        continuation: Callable[[ClientCallDetails, _TRequest], UnaryStreamCall[_TRequest, _TResponse]],
        client_call_details: ClientCallDetails,
        request: _TRequest,
    ) -> AsyncIterable[_TResponse] | UnaryStreamCall[_TRequest, _TResponse]: ...

class StreamUnaryClientInterceptor(Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    async def intercept_stream_unary(
        self,
        continuation: Callable[[ClientCallDetails, _TRequest], StreamUnaryCall[_TRequest, _TResponse]],
        client_call_details: ClientCallDetails,
        request_iterator: AsyncIterable[_TRequest] | Iterable[_TRequest],
    ) -> AsyncIterable[_TResponse] | UnaryStreamCall[_TRequest, _TResponse]: ...

class StreamStreamClientInterceptor(Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    async def intercept_stream_stream(
        self,
        continuation: Callable[[ClientCallDetails, _TRequest], StreamStreamCall[_TRequest, _TResponse]],
        client_call_details: ClientCallDetails,
        request_iterator: AsyncIterable[_TRequest] | Iterable[_TRequest],
    ) -> AsyncIterable[_TResponse] | StreamStreamCall[_TRequest, _TResponse]: ...

# Server-Side Interceptor:

class ServerInterceptor(Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    async def intercept_service(
        self,
        continuation: Callable[[HandlerCallDetails], Awaitable[RpcMethodHandler[_TRequest, _TResponse]]],
        handler_call_details: HandlerCallDetails,
    ) -> RpcMethodHandler[_TRequest, _TResponse]: ...

# Multi-Callable Interfaces:

class UnaryUnaryMultiCallable(Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def __call__(
        self,
        request: _TRequest,
        *,
        timeout: float | None = None,
        metadata: _MetadataType | None = None,
        credentials: CallCredentials | None = None,
        wait_for_ready: bool | None = None,
        compression: Compression | None = None,
    ) -> UnaryUnaryCall[_TRequest, _TResponse]: ...

class UnaryStreamMultiCallable(Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def __call__(
        self,
        request: _TRequest,
        *,
        timeout: float | None = None,
        metadata: _MetadataType | None = None,
        credentials: CallCredentials | None = None,
        wait_for_ready: bool | None = None,
        compression: Compression | None = None,
    ) -> UnaryStreamCall[_TRequest, _TResponse]: ...

class StreamUnaryMultiCallable(Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def __call__(
        self,
        request_iterator: AsyncIterator[_TRequest] | Iterator[_TRequest] | None = None,
        timeout: float | None = None,
        metadata: _MetadataType | None = None,
        credentials: CallCredentials | None = None,
        wait_for_ready: bool | None = None,
        compression: Compression | None = None,
    ) -> StreamUnaryCall[_TRequest, _TResponse]: ...

class StreamStreamMultiCallable(Generic[_TRequest, _TResponse], metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def __call__(
        self,
        request_iterator: AsyncIterator[_TRequest] | Iterator[_TRequest] | None = None,
        timeout: float | None = None,
        metadata: _MetadataType | None = None,
        credentials: CallCredentials | None = None,
        wait_for_ready: bool | None = None,
        compression: Compression | None = None,
    ) -> StreamStreamCall[_TRequest, _TResponse]: ...

# Metadata:

_MetadataKey: TypeAlias = str
_MetadataValue: TypeAlias = str | bytes
_MetadatumType: TypeAlias = tuple[_MetadataKey, _MetadataValue]
_MetadataType: TypeAlias = Metadata | Sequence[_MetadatumType]
_T = TypeVar("_T")

class Metadata(Mapping[_MetadataKey, _MetadataValue]):
    def __init__(self, *args: tuple[_MetadataKey, _MetadataValue]) -> None: ...
    @classmethod
    def from_tuple(cls, raw_metadata: tuple[_MetadataKey, _MetadataValue]) -> Metadata: ...
    def add(self, key: _MetadataKey, value: _MetadataValue) -> None: ...
    def __len__(self) -> int: ...
    def __getitem__(self, key: _MetadataKey) -> _MetadataValue: ...
    def __setitem__(self, key: _MetadataKey, value: _MetadataValue) -> None: ...
    def __delitem__(self, key: _MetadataKey) -> None: ...
    def delete_all(self, key: _MetadataKey) -> None: ...
    def __iter__(self) -> Iterator[_MetadataKey]: ...
    @overload
    def get(self, key: _MetadataKey, default: None = None) -> _MetadataValue | None: ...
    @overload
    def get(self, key: _MetadataKey, default: _MetadataValue) -> _MetadataValue: ...
    @overload
    def get(self, key: _MetadataKey, default: _T) -> _MetadataValue | _T: ...
    def get_all(self, key: _MetadataKey) -> list[_MetadataValue]: ...
    def set_all(self, key: _MetadataKey, values: list[_MetadataValue]) -> None: ...
    def __contains__(self, key: object) -> bool: ...
    def __eq__(self, other: object) -> bool: ...
    def __add__(self, other: Any) -> Metadata: ...
