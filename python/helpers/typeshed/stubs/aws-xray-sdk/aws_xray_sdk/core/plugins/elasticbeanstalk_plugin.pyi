from logging import Logger
from typing import Final

log: Logger
CONF_PATH: Final[str]
SERVICE_NAME: Final[str]
ORIGIN: Final[str]

def initialize() -> None: ...
