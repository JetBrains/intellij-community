from logging import Logger
from typing import Pattern
from typing_extensions import TypedDict

class _FinalResultType(TypedDict):
    encoding: str
    confidence: float
    language: str

class _IntermediateResultType(TypedDict):
    encoding: str | None
    confidence: float
    language: str | None

class UniversalDetector(object):
    MINIMUM_THRESHOLD: float
    HIGH_BYTE_DETECTOR: Pattern[bytes]
    ESC_DETECTOR: Pattern[bytes]
    WIN_BYTE_DETECTOR: Pattern[bytes]
    ISO_WIN_MAP: dict[str, str]

    result: _IntermediateResultType
    done: bool
    lang_filter: int
    logger: Logger
    def __init__(self, lang_filter: int = ...) -> None: ...
    def reset(self) -> None: ...
    def feed(self, byte_str: bytes) -> None: ...
    def close(self) -> _FinalResultType: ...
