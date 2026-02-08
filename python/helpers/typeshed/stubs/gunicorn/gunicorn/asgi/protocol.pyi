import asyncio
from collections.abc import Iterable

from gunicorn.config import Config
from gunicorn.glogging import Logger as GLogger
from gunicorn.workers.gasgi import ASGIWorker

from .._types import _ASGIAppType

class ASGIResponseInfo:
    status: str | int
    sent: int
    headers: list[tuple[str, str]]

    def __init__(self, status: str | int, headers: Iterable[tuple[str | bytes, str | bytes]], sent: int) -> None: ...

class ASGIProtocol(asyncio.Protocol):
    worker: ASGIWorker
    cfg: Config
    log: GLogger
    app: _ASGIAppType
    transport: asyncio.BaseTransport | None
    reader: asyncio.StreamReader | None
    writer: asyncio.BaseTransport | None
    req_count: int

    def __init__(self, worker: ASGIWorker) -> None: ...
