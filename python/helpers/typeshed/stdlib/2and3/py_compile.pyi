# Stubs for py_compile (Python 2 and 3)
import sys

from typing import Optional, List, Text, AnyStr, Union

_EitherStr = Union[bytes, Text]

class PyCompileError(Exception):
    exc_type_name = ...  # type: str
    exc_value = ...  # type: BaseException
    file = ...  # type: str
    msg = ...  # type: str
    def __init__(self, exc_type: str, exc_value: BaseException, file: str, msg: str = ...) -> None: ...

if sys.version_info >= (3, 2):
    def compile(file: AnyStr, cfile: Optional[AnyStr] = ..., dfile: Optional[AnyStr] = ..., doraise: bool = ..., optimize: int = ...) -> Optional[AnyStr]: ...
else:
    def compile(file: _EitherStr, cfile: Optional[_EitherStr] = ..., dfile: Optional[_EitherStr] = ..., doraise: bool = ...) -> None: ...

def main(args: Optional[List[Text]] = ...) -> int: ...
