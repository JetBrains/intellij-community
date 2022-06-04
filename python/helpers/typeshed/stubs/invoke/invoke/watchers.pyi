import threading
from typing import Iterable

class StreamWatcher(threading.local):
    def submit(self, stream) -> Iterable[str]: ...

class Responder(StreamWatcher):
    pattern: str
    response: str
    index: int
    def __init__(self, pattern: str, response: str) -> None: ...
    def pattern_matches(self, stream: str, pattern: str, index_attr: str) -> Iterable[str]: ...
    def submit(self, stream: str) -> Iterable[str]: ...

class FailingResponder(Responder):
    sentinel: str
    failure_index: int
    tried: bool
    def __init__(self, pattern: str, response: str, sentinel: str) -> None: ...
    def submit(self, stream: str) -> Iterable[str]: ...
