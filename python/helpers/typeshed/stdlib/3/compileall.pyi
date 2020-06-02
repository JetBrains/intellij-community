# Stubs for compileall (Python 3)

import os
import sys
from typing import Any, Optional, Union, Pattern

if sys.version_info < (3, 6):
    _Path = Union[str, bytes]
    _SuccessType = bool
else:
    _Path = Union[str, bytes, os.PathLike]
    _SuccessType = int

if sys.version_info >= (3, 7):
    from py_compile import PycInvalidationMode

if sys.version_info >= (3, 9):
    def compile_dir(
        dir: _Path,
        maxlevels: Optional[int] = ...,
        ddir: Optional[_Path] = ...,
        force: bool = ...,
        rx: Optional[Pattern[Any]] = ...,
        quiet: int = ...,
        legacy: bool = ...,
        optimize: int = ...,
        workers: int = ...,
        invalidation_mode: Optional[PycInvalidationMode] = ...,
        *,
        stripdir: Optional[str] = ...,  # TODO: change to Optional[_Path] once https://bugs.python.org/issue40447 is resolved
        prependdir: Optional[_Path] = ...,
        limit_sl_dest: Optional[_Path] = ...,
    ) -> _SuccessType: ...
    def compile_file(
        fullname: _Path,
        ddir: Optional[_Path] = ...,
        force: bool = ...,
        rx: Optional[Pattern[Any]] = ...,
        quiet: int = ...,
        legacy: bool = ...,
        optimize: int = ...,
        invalidation_mode: Optional[PycInvalidationMode] = ...,
        *,
        stripdir: Optional[str] = ...,  # TODO: change to Optional[_Path] once https://bugs.python.org/issue40447 is resolved
        prependdir: Optional[_Path] = ...,
        limit_sl_dest: Optional[_Path] = ...,
    ) -> _SuccessType: ...
elif sys.version_info >= (3, 7):
    def compile_dir(
        dir: _Path,
        maxlevels: int = ...,
        ddir: Optional[_Path] = ...,
        force: bool = ...,
        rx: Optional[Pattern[Any]] = ...,
        quiet: int = ...,
        legacy: bool = ...,
        optimize: int = ...,
        workers: int = ...,
        invalidation_mode: Optional[PycInvalidationMode] = ...,
    ) -> _SuccessType: ...
    def compile_file(
        fullname: _Path,
        ddir: Optional[_Path] = ...,
        force: bool = ...,
        rx: Optional[Pattern[Any]] = ...,
        quiet: int = ...,
        legacy: bool = ...,
        optimize: int = ...,
        invalidation_mode: Optional[PycInvalidationMode] = ...,
    ) -> _SuccessType: ...

else:
    # rx can be any object with a 'search' method; once we have Protocols we can change the type
    def compile_dir(
        dir: _Path,
        maxlevels: int = ...,
        ddir: Optional[_Path] = ...,
        force: bool = ...,
        rx: Optional[Pattern[Any]] = ...,
        quiet: int = ...,
        legacy: bool = ...,
        optimize: int = ...,
        workers: int = ...,
    ) -> _SuccessType: ...
    def compile_file(
        fullname: _Path,
        ddir: Optional[_Path] = ...,
        force: bool = ...,
        rx: Optional[Pattern[Any]] = ...,
        quiet: int = ...,
        legacy: bool = ...,
        optimize: int = ...,
    ) -> _SuccessType: ...

if sys.version_info >= (3, 7):
    def compile_path(
        skip_curdir: bool = ...,
        maxlevels: int = ...,
        force: bool = ...,
        quiet: int = ...,
        legacy: bool = ...,
        optimize: int = ...,
        invalidation_mode: Optional[PycInvalidationMode] = ...,
    ) -> _SuccessType: ...
else:
    def compile_path(
        skip_curdir: bool = ...,
        maxlevels: int = ...,
        force: bool = ...,
        quiet: int = ...,
        legacy: bool = ...,
        optimize: int = ...,
    ) -> _SuccessType: ...
