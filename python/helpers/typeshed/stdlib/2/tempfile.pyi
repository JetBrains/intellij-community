# Stubs for tempfile
# Ron Murawski <ron@horizonchess.com>

# based on http: //docs.python.org/3.3/library/tempfile.html
# Adapted for Python 2.7 by Michal Pokorny

# TODO: Don't use basestring. Use Union[str, bytes] or AnyStr for arguments.
#       Avoid using Union[str, bytes] for return values, as it implies that
#       an isinstance() check will often be required, which is inconvenient.

from typing import Tuple, IO, Union, AnyStr, Any, overload, Iterator, List, Type, Optional

import thread
import random

TMP_MAX = ...  # type: int
tempdir = ...  # type: str
template = ...  # type: str
_name_sequence = ...  # type: Optional[_RandomNameSequence]

class _RandomNameSequence:
    _rng = ...  # type: random.Random
    _rng_pid = ...  # type: int
    characters = ...  # type: str
    mutex = ...  # type: thread.LockType
    rng = ...  # type: random.Random
    def __iter__(self) -> "_RandomNameSequence": ...
    def next(self) -> str: ...
    # from os.path:
    def normcase(self, path: AnyStr) -> AnyStr: ...

class _TemporaryFileWrapper(IO[str]):
    close_called = ...  # type: bool
    delete = ...  # type: bool
    file = ...  # type: IO
    name = ...  # type: Any
    def __init__(self, file: IO, name, delete: bool = ...) -> None: ...
    def __del__(self) -> None: ...
    def __enter__(self) -> "_TemporaryFileWrapper": ...
    def __exit__(self, exc, value, tb) -> bool: ...
    def __getattr__(self, name: unicode) -> Any: ...
    def close(self) -> None: ...
    def unlink(self, path: unicode) -> None: ...

# TODO text files

def TemporaryFile(
    mode: Union[bytes, unicode] = ...,
    bufsize: int = ...,
    suffix: Union[bytes, unicode] = ...,
    prefix: Union[bytes, unicode] = ...,
    dir: Union[bytes, unicode] = ...
) -> _TemporaryFileWrapper:
    ...

def NamedTemporaryFile(
    mode: Union[bytes, unicode] = ...,
    bufsize: int = ...,
    suffix: Union[bytes, unicode] = ...,
    prefix: Union[bytes, unicode] = ...,
    dir: Union[bytes, unicode] = ...,
    delete: bool = ...
) -> _TemporaryFileWrapper:
    ...

def SpooledTemporaryFile(
    max_size: int = ...,
    mode: Union[bytes, unicode] = ...,
    buffering: int = ...,
    suffix: Union[bytes, unicode] = ...,
    prefix: Union[bytes, unicode] = ...,
    dir: Union[bytes, unicode] = ...
) -> _TemporaryFileWrapper:
    ...

class TemporaryDirectory:
    name = ...  # type: Any  # Can be str or unicode
    def __init__(self,
                 suffix: Union[bytes, unicode] = ...,
                 prefix: Union[bytes, unicode] = ...,
                 dir: Union[bytes, unicode] = ...) -> None: ...
    def cleanup(self) -> None: ...
    def __enter__(self) -> Any: ...  # Can be str or unicode
    def __exit__(self, type, value, traceback) -> bool: ...

@overload
def mkstemp() -> Tuple[int, str]: ...
@overload
def mkstemp(suffix: AnyStr = ..., prefix: AnyStr = ..., dir: Optional[AnyStr] = ...,
            text: bool = ...) -> Tuple[int, AnyStr]: ...
@overload
def mkdtemp() -> str: ...
@overload
def mkdtemp(suffix: AnyStr = ..., prefix: AnyStr = ..., dir: Optional[AnyStr] = ...) -> AnyStr: ...
@overload
def mktemp() -> str: ...
@overload
def mktemp(suffix: AnyStr = ..., prefix: AnyStr = ..., dir: Optional[AnyStr] = ...) -> AnyStr: ...
def gettempdir() -> str: ...
def gettempprefix() -> str: ...

def _candidate_tempdir_list() -> List[str]: ...
def _get_candidate_names() -> Optional[_RandomNameSequence]: ...
def _get_default_tempdir() -> str: ...
