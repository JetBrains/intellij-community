from distutils.core import Extension as _Extension
from typing import Any

have_pyrex: Any

class Extension(_Extension):
    py_limited_api: Any
    def __init__(self, name, sources, *args, **kw) -> None: ...

class Library(Extension): ...
