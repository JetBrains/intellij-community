from typing import Any

from ...client import Pipeline as ClientPipeline
from .commands import TimeSeriesCommands

class TimeSeries(TimeSeriesCommands):
    MODULE_CALLBACKS: dict[str, Any]
    client: Any
    execute_command: Any
    def __init__(self, client: Any | None = ..., **kwargs) -> None: ...
    def pipeline(self, transaction: bool = ..., shard_hint: Any | None = ...) -> Pipeline: ...

class Pipeline(TimeSeriesCommands, ClientPipeline): ...  # type: ignore[misc]
