from __future__ import annotations

from collections.abc import Callable
from concurrent.futures.thread import ThreadPoolExecutor
from typing import Awaitable, TypeVar

import grpc
import grpc.aio

RequestT = TypeVar("RequestT")
ResponseT = TypeVar("ResponseT")


class NoopInterceptor(grpc.ServerInterceptor):
    def intercept_service(
        self,
        continuation: Callable[[grpc.HandlerCallDetails], grpc.RpcMethodHandler[RequestT, ResponseT] | None],
        handler_call_details: grpc.HandlerCallDetails,
    ) -> grpc.RpcMethodHandler[RequestT, ResponseT] | None:
        return continuation(handler_call_details)


grpc.server(interceptors=[NoopInterceptor()], thread_pool=ThreadPoolExecutor())


class NoopAioInterceptor(grpc.aio.ServerInterceptor):
    async def intercept_service(
        self,
        continuation: Callable[[grpc.HandlerCallDetails], Awaitable[grpc.RpcMethodHandler[RequestT, ResponseT]]],
        handler_call_details: grpc.HandlerCallDetails,
    ) -> grpc.RpcMethodHandler[RequestT, ResponseT]:
        return await continuation(handler_call_details)


grpc.aio.server(interceptors=[NoopAioInterceptor()])
