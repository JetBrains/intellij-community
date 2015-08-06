from typing import overload

class C:
    @overload
    def __getitem__(self, key: int) -> int: ...
    @overload
    def __getitem__(self, key: str) -> str: ...
