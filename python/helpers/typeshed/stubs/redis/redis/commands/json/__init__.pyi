from typing import Any

from ...client import Pipeline as ClientPipeline
from .commands import JSONCommands

class JSON(JSONCommands):
    MODULE_CALLBACKS: dict[str, Any]
    client: Any
    execute_command: Any
    MODULE_VERSION: Any | None
    def __init__(self, client, version: Any | None = ..., decoder=..., encoder=...) -> None: ...
    def pipeline(self, transaction: bool = ..., shard_hint: Any | None = ...) -> Pipeline: ...

class Pipeline(JSONCommands, ClientPipeline): ...  # type: ignore[misc]
