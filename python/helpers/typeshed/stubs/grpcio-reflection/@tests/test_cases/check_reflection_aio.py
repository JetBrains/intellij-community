from __future__ import annotations

from typing import cast

import grpc.aio
from grpc_reflection.v1alpha.reflection import enable_server_reflection

server = cast(grpc.aio.Server, None)
enable_server_reflection(["foo"], server, None)
