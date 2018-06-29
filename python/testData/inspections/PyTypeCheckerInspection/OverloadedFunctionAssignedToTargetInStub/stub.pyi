from typing import overload

@overload
def good(default: int) -> int: ...
@overload
def good(default: str) -> str: ...

bad = good