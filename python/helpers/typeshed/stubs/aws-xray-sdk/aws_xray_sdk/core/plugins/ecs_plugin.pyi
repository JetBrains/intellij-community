from logging import Logger
from typing import Final

log: Logger
SERVICE_NAME: Final[str]
ORIGIN: Final[str]

def initialize() -> None: ...
