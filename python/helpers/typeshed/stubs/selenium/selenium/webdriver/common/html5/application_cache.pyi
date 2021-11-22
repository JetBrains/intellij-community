from typing import Any

class ApplicationCache:
    UNCACHED: int
    IDLE: int
    CHECKING: int
    DOWNLOADING: int
    UPDATE_READY: int
    OBSOLETE: int
    driver: Any
    def __init__(self, driver) -> None: ...
    @property
    def status(self): ...
