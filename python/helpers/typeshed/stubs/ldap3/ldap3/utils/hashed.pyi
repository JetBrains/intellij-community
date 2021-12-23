from typing import Any

algorithms_table: Any
salted_table: Any

def hashed(algorithm, value, salt: Any | None = ..., raw: bool = ..., encoding: str = ...): ...
