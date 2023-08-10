from _typeshed import Incomplete
from typing import Any

def __getattr__(name: str) -> Incomplete: ...

class OptionManager:
    def QueryOptions(self, name: str) -> list[OptionValue]: ...

class OptionValue:
    value: Any
    key: str
