# Stubs for compileall (Python 3)

import os
import sys
from typing import Optional, Union, Pattern

if sys.version_info < (3, 6):
    _Path = Union[str, bytes]
    _SuccessType = bool
else:
    _Path = Union[str, bytes, os.PathLike]
    _SuccessType = int

# rx can be any object with a 'search' method; once we have Protocols we can change the type
if sys.version_info < (3, 5):
    def compile_dir(dir: _Path, maxlevels: int = ..., ddir: Optional[_Path] = ..., force: bool = ..., rx: Optional[Pattern] = ..., quiet: int = ..., legacy: bool = ..., optimize: int = ...) -> _SuccessType: ...
else:
    def compile_dir(dir: _Path, maxlevels: int = ..., ddir: Optional[_Path] = ..., force: bool = ..., rx: Optional[Pattern] = ..., quiet: int = ..., legacy: bool = ..., optimize: int = ..., workers: int = ...) -> _SuccessType: ...
def compile_file(fullname: _Path, ddir: Optional[_Path] = ..., force: bool = ..., rx: Optional[Pattern] = ..., quiet: int = ..., legacy: bool = ..., optimize: int = ...) -> _SuccessType: ...
def compile_path(skip_curdir: bool = ..., maxlevels: int = ..., force: bool = ..., quiet: int = ..., legacy: bool = ..., optimize: int = ...) -> _SuccessType: ...
