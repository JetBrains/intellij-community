# Stubs for compileall (Python 2)

from typing import Optional, Pattern, Union

_Path = Union[str, bytes]

# fx can be any object with a 'search' method; once we have Protocols we can change the type
def compile_dir(dir: _Path, maxlevels: int = ..., ddir: Optional[_Path] = ..., force: bool = ..., rx: Optional[Pattern] = ..., quiet: int = ...) -> None: ...
def compile_file(fullname: _Path, ddir: Optional[_Path] = ..., force: bool = ..., rx: Optional[Pattern] = ..., quiet: int = ...) -> None: ...
def compile_path(skip_curdir: bool = ..., maxlevels: int = ..., force: bool = ..., quiet: int = ...) -> None: ...
