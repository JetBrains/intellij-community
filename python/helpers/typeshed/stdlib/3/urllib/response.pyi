# private module, we only expose what's needed

from typing import BinaryIO, Mapping, Optional
from types import TracebackType

class addinfourl(BinaryIO):
    headers = ...  # type: Mapping[str, str]
    url = ...  # type: str
    code = ...  # type: int
    def info(self) -> Mapping[str, str]: ...
    def geturl(self) -> str: ...
