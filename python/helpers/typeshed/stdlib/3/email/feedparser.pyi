# Stubs for email.feedparser (Python 3.4)

from typing import Callable
import sys
from email.message import Message
if sys.version_info >= (3, 3):
    from email.policy import Policy

class FeedParser:
    if sys.version_info >= (3, 3):
        def __init__(self, _factory: Callable[[], Message] = ..., *,
                     policy: Policy = ...) -> None: ...
    else:
        def __init__(self,
                     _factory: Callable[[], Message] = ...) -> None: ...
    def feed(self, data: str) -> None: ...
    def close(self) -> Message: ...

if sys.version_info >= (3, 2):
    class BytesFeedParser:
        if sys.version_info >= (3, 3):
            def __init__(self, _factory: Callable[[], Message] = ..., *,
                         policy: Policy = ...) -> None: ...
        else:
            def __init__(self,
                         _factory: Callable[[], Message] = ...) -> None: ...
        def feed(self, data: str) -> None: ...
        def close(self) -> Message: ...
