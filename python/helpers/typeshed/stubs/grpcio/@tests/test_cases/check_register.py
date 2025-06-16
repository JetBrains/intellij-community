from __future__ import annotations

from typing import Any

import grpc


@grpc.Call.register
class CallProxy:
    def __init__(self, target: grpc.Call) -> None:
        self._target = target

    def __getattr__(self, name: str) -> Any:
        return getattr(self._target, name)
