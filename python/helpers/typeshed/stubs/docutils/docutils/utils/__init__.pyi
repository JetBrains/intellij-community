import optparse
from builtins import list as _list  # alias to avoid name clashes with fields named list
from collections.abc import Iterable
from typing import Any
from typing_extensions import Literal, TypeAlias

from docutils import ApplicationError
from docutils.io import FileOutput
from docutils.nodes import document

class DependencyList:
    list: _list[str]
    file: FileOutput | None
    def __init__(self, output_file: str | None = ..., dependencies: Iterable[str] = ...) -> None: ...
    def set_output(self, output_file: str | None) -> None: ...
    def add(self, *filenames: str) -> None: ...
    def close(self) -> None: ...

_SystemMessageLevel: TypeAlias = Literal[0, 1, 2, 3, 4]

class Reporter:
    DEBUG_LEVEL: Literal[0]
    INFO_LEVEL: Literal[1]
    WARNING_LEVEL: Literal[2]
    ERROR_LEVEL: Literal[3]
    SEVERE_LEVEL: Literal[4]

    source: str
    report_level: _SystemMessageLevel
    halt_level: _SystemMessageLevel
    def __getattr__(self, __name: str) -> Any: ...  # incomplete

class SystemMessage(ApplicationError):
    level: _SystemMessageLevel
    def __init__(self, system_message: object, level: _SystemMessageLevel): ...

def new_reporter(source_path: str, settings: optparse.Values) -> Reporter: ...
def new_document(source_path: str, settings: optparse.Values | None = ...) -> document: ...
def __getattr__(name: str) -> Any: ...  # incomplete
