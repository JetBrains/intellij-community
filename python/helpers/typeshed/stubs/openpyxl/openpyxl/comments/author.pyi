from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class AuthorList(Serialisable):
    tagname: str
    author: Any
    authors: Any
    def __init__(self, author=...) -> None: ...
