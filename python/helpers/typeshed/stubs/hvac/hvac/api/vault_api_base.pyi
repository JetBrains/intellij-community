from abc import ABCMeta
from logging import Logger
from typing import Any

from hvac.adapters import Adapter

logger: Logger

class VaultApiBase(metaclass=ABCMeta):
    def __init__(self, adapter: Adapter[Any]) -> None: ...
