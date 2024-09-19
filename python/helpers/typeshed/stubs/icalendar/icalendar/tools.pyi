from typing import Final

from .prop import vText

class UIDGenerator:
    chars: Final[list[str]]
    @staticmethod
    def rnd_string(length: int = 16) -> str: ...
    @staticmethod
    def uid(host_name: str = "example.com", unique: str = "") -> vText: ...
