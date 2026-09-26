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
    # grpc.aio.Metadata is a Collection that iterates as (key, value) tuples,
    # not a Mapping that iterates bare keys.
    for key, value in metadata:
        assert_type(key, str)
        assert_type(value, grpc.aio._MetadataValue)
