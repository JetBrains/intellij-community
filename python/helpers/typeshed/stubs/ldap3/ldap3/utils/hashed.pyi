from typing import Any

algorithms_table: Any
salted_table: Any

def hashed(algorithm, value, salt=None, raw: bool = False, encoding: str = "utf-8"): ...
