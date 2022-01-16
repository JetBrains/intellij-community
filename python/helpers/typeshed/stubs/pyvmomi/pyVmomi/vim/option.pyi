from typing import Any

def __getattr__(name: str) -> Any: ...  # incomplete

class OptionManager:
    def QueryOptions(self, name: str) -> list[OptionValue]: ...

class OptionValue:
    value: Any
