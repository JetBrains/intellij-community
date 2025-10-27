from __future__ import annotations

from typing import Any, cast
from typing_extensions import assert_type

import grpc


class Request:
    pass


class Response:
    pass


def unary_unary_call(rq: Request, ctx: grpc.ServicerContext) -> Response:
    assert_type(rq, Request)
    return Response()


class ServiceHandler(grpc.ServiceRpcHandler):
    def service_name(self) -> str:
        return "hello"

    def service(self, handler_call_details: grpc.HandlerCallDetails) -> grpc.RpcMethodHandler[Any, Any] | None:
        rpc = grpc.RpcMethodHandler[Request, Response]()
        rpc.unary_unary = unary_unary_call
        return rpc


h = ServiceHandler()
ctx = cast(grpc.ServicerContext, None)
svc = h.service(grpc.HandlerCallDetails())
if svc is not None and svc.unary_unary is not None:
    svc.unary_unary(Request(), ctx)
