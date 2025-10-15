from types import FrameType
from typing import Any
from typing_extensions import TypeAlias

from gunicorn.workers.base import Worker

IOLoop: TypeAlias = Any  # tornado IOLoop class
PeriodicCallback: TypeAlias = Any  # tornado PeriodicCallback class

TORNADO5: bool

class TornadoWorker(Worker):
    alive: bool
    server_alive: bool
    ioloop: IOLoop
    callbacks: list[PeriodicCallback]

    @classmethod
    def setup(cls) -> None: ...
    def handle_exit(self, sig: int, frame: FrameType | None) -> None: ...
    def handle_request(self) -> None: ...
    def watchdog(self) -> None: ...
    def heartbeat(self) -> None: ...
    def init_process(self) -> None: ...
    def run(self) -> None: ...
