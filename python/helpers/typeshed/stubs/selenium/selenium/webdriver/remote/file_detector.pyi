import abc
from typing import Any

class FileDetector(metaclass=abc.ABCMeta):
    __metaclass__: Any
    @abc.abstractmethod
    def is_local_file(self, *keys): ...

class UselessFileDetector(FileDetector):
    def is_local_file(self, *keys) -> None: ...

class LocalFileDetector(FileDetector):
    def is_local_file(self, *keys): ...
