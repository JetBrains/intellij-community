# Stubs for email.parser (Python 3.4)

import email.feedparser
from email.message import Message
import sys
from typing import Callable, Optional, TextIO, BinaryIO
if sys.version_info >= (3, 3):
    from email.policy import Policy

FeedParser = email.feedparser.FeedParser
BytesFeedParser = email.feedparser.BytesFeedParser

class Parser:
    if sys.version_info >= (3, 3):
        def __init__(self, _class: Callable[[], Message] = ..., *,
                     policy: Policy = ...) -> None: ...
    else:
        # TODO `strict` is positional
        def __init__(self,
                     _class: Callable[[], Message] = ..., *,
                     strict: Optional[bool]) -> None: ...
    def parse(self, fp: TextIO, headersonly: bool = ...) -> Message: ...
    def parsestr(self, text: str, headersonly: bool = ...) -> Message: ...

class HeaderParser(Parser):
    if sys.version_info >= (3, 3):
        def __init__(self, _class: Callable[[], Message] = ..., *,
                     policy: Policy = ...) -> None: ...
    else:
        # TODO `strict` is positional
        def __init__(self,
                     _class: Callable[[], Message] = ..., *,
                     strict: Optional[bool]) -> None: ...
    def parse(self, fp: TextIO, headersonly: bool = ...) -> Message: ...
    def parsestr(self, text: str, headersonly: bool = ...) -> Message: ...

if sys.version_info >= (3, 3):
    class BytesHeaderParser(BytesParser):
        if sys.version_info >= (3, 3):
            def __init__(self, _class: Callable[[], Message] = ..., *,
                         policy: Policy = ...) -> None: ...
        else:
            # TODO `strict` is positional
            def __init__(self,
                         _class: Callable[[], Message] = ..., *,
                         strict: Optional[bool]) -> None: ...
        def parse(self, fp: BinaryIO, headersonly: bool = ...) -> Message: ...
        def parsestr(self, text: str, headersonly: bool = ...) -> Message: ...

if sys.version_info >= (3, 2):
    class BytesParser:
        if sys.version_info >= (3, 3):
            def __init__(self, _class: Callable[[], Message] = ..., *,
                         policy: Policy = ...) -> None: ...
        else:
            # TODO `strict` is positional
            def __init__(self,
                         _class: Callable[[], Message] = ..., *,
                         strict: Optional[bool]) -> None: ...
        def parse(self, fp: BinaryIO, headersonly: bool = ...) -> Message: ...
        def parsestr(self, text: str, headersonly: bool = ...) -> Message: ...
