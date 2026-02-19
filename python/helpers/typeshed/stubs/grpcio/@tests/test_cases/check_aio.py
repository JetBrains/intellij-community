from __future__ import annotations

from typing import cast
from typing_extensions import assert_type

import grpc.aio

# Interceptor casts
client_interceptors: list[grpc.aio.ClientInterceptor] = []
grpc.aio.insecure_channel("target", interceptors=client_interceptors)

server_interceptors: list[grpc.aio.ServerInterceptor] = []
grpc.aio.server(interceptors=server_interceptors)


# Metadata
async def metadata() -> None:
    metadata = await cast(grpc.aio.Call, None).initial_metadata()
    assert_type(metadata["foo"], grpc.aio._MetadataValue)
    for k in metadata:
        assert_type(k, str)

    for k, v in metadata.items():
        assert_type(k, str)
        assert_type(v, grpc.aio._MetadataValue)
