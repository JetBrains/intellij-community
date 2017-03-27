# Stubs for email (Python 3.4)

from typing import Callable, Optional, IO
import sys
from email.message import Message
if sys.version_info >= (3, 3):
    from email.policy import Policy

if sys.version_info >= (3, 3):
    def message_from_string(s: str, _class: Callable[[], Message] = ..., *,
                            policy: Policy = ...) -> Message: ...
    def message_from_bytes(s: bytes, _class: Callable[[], Message] = ..., *,
                           policy: Policy = ...) -> Message: ...
    def message_from_file(fp: IO[str], _class: Callable[[], Message] = ..., *,
                           policy: Policy = ...) -> Message: ...
    def message_from_binary_file(fp: IO[bytes],
                                 _class: Callable[[], Message] = ..., *,
                                 policy: Policy = ...) -> Message: ...
elif sys.version_info >= (3, 2):
    def message_from_string(s: str,
                            _class: Callable[[], Message] = ..., *,
                            strict: Optional[bool] = ...) -> Message: ...
    def message_from_bytes(s: bytes,
                           _class: Callable[[], Message] = ..., *,
                           strict: Optional[bool] = ...) -> Message: ...
    def message_from_file(fp: IO[str],
                          _class: Callable[[], Message] = ..., *,
                          strict: Optional[bool] = ...) -> Message: ...
    def message_from_binary_file(fp: IO[bytes],
                                 _class: Callable[[], Message] = ..., *,
                                 strict: Optional[bool] = ...) -> Message: ...

# Names in __all__ with no definition:
#   base64mime
#   charset
#   encoders
#   errors
#   feedparser
#   generator
#   header
#   iterators
#   message
#   mime
#   parser
#   quoprimime
#   utils
