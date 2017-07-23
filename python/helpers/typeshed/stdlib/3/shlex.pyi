# Stubs for shlex

# Based on http://docs.python.org/3.2/library/shlex.html

from typing import List, Tuple, Any, TextIO, Union, Optional

def split(s: str, comments: bool = ...,
          posix: bool = ...) -> List[str]: ...

# Added in 3.3, use (undocumented) pipes.quote in previous versions.
def quote(s: str) -> str: ...

class shlex:
    commenters = ...  # type: str
    wordchars = ...  # type: str
    whitespace = ...  # type: str
    escape = ...  # type: str
    quotes = ...  # type: str
    escapedquotes = ...  # type: str
    whitespace_split = ...  # type: bool
    infile = ...  # type: str
    instream = ...  # type: TextIO
    source = ...  # type: str
    debug = 0
    lineno = 0
    token = ...  # type: str
    eof = ...  # type: str

    def __init__(self, instream: Union[str, TextIO] = ..., infile: Optional[str] = ...,
                 posix: bool = ...) -> None: ...
    def get_token(self) -> str: ...
    def push_token(self, tok: str) -> None: ...
    def read_token(self) -> str: ...
    def sourcehook(self, filename: str) -> Tuple[str, TextIO]: ...
    # TODO argument types
    def push_source(self, newstream: Any, newfile: Any = ...) -> None: ...
    def pop_source(self) -> None: ...
    def error_leader(self, infile: str = ...,
                     lineno: int = ...) -> None: ...
