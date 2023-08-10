from collections.abc import Generator
from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

mimetypes: Any

class FileExtension(Serialisable):
    tagname: str
    Extension: Any
    ContentType: Any
    def __init__(self, Extension, ContentType) -> None: ...

class Override(Serialisable):
    tagname: str
    PartName: Any
    ContentType: Any
    def __init__(self, PartName, ContentType) -> None: ...

DEFAULT_TYPES: Any
DEFAULT_OVERRIDE: Any

class Manifest(Serialisable):
    tagname: str
    Default: Any
    Override: Any
    path: str
    __elements__: Any
    def __init__(self, Default=..., Override=...) -> None: ...
    @property
    def filenames(self): ...
    @property
    def extensions(self): ...
    def to_tree(self): ...
    def __contains__(self, content_type): ...
    def find(self, content_type): ...
    def findall(self, content_type) -> Generator[Any, None, None]: ...
    def append(self, obj) -> None: ...
