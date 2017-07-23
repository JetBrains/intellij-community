# Stubs for email.generator (Python 3.4)

from typing import TextIO, Optional
import sys
from email.message import Message
if sys.version_info >= (3, 3):
    from email.policy import Policy

class Generator:
    def clone(self, fp: TextIO) -> 'Generator': ...
    def write(self, s: str) -> None: ...
    if sys.version_info >= (3, 3):
        def __init__(self, outfp: TextIO, mangle_from_: bool = ...,
                     maxheaderlen: int = ..., *,
                     policy: Policy = ...) -> None: ...
    else:
        def __init__(self, outfp: TextIO,
                     mangle_from_: bool = ...,
                     maxheaderlen: int = ...) -> None: ...
    if sys.version_info >= (3, 2):
        def flatten(self, msg: Message, unixfrom: bool = ...,
                    linesep: Optional[str] =...) -> None: ...
    else:
        def flatten(self, msg: Message,
                    unixfrom: bool = ...) -> None: ...

if sys.version_info >= (3, 2):
    class BytesGenerator:
        def clone(self, fp: TextIO) -> 'Generator': ...
        def write(self, s: str) -> None: ...
        if sys.version_info >= (3, 3):
            def __init__(self, outfp: TextIO, mangle_from_: bool = ...,
                         maxheaderlen: int = ..., *,
                         policy: Policy = ...) -> None: ...
        else:
            def __init__(self, outfp: TextIO,
                         mangle_from_: bool = ...,
                         maxheaderlen: int = ...) -> None: ...
        def flatten(self, msg: Message, unixfrom: bool = ...,
                    linesep: Optional[str] =...) -> None: ...

class DecodedGenerator(Generator):
    # TODO `fmt` is positional
    def __init__(self, outfp: TextIO, mangle_from_: bool = ...,
                 maxheaderlen: int = ..., *, fmt: Optional[str]) -> None: ...
