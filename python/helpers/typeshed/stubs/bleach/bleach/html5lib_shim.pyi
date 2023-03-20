from collections.abc import Generator, Iterable
from typing import Any

class HTMLParser:  # actually html5lib.HTMLParser
    def __getattr__(self, __name: str) -> Any: ...  # incomplete

class Filter:  # actually html5lib.filters.base.Filter
    def __getattr__(self, __name: str) -> Any: ...  # incomplete

class SanitizerFilter:  # actually html5lib.filters.sanitizer.Filter
    def __getattr__(self, __name: str) -> Any: ...  # incomplete

class HTMLSerializer:  # actually html5lib.serializer.HTMLSerializer
    def __getattr__(self, __name: str) -> Any: ...  # incomplete

class BleachHTMLParser(HTMLParser):
    tags: list[str] | None
    strip: bool
    consume_entities: bool
    def __init__(self, tags: Iterable[str] | None, strip: bool, consume_entities: bool, **kwargs) -> None: ...

class BleachHTMLSerializer(HTMLSerializer):
    escape_rcdata: bool
    def escape_base_amp(self, stoken: str) -> Generator[str, None, None]: ...
    def serialize(self, treewalker, encoding: str | None = ...) -> Generator[str, None, None]: ...

def __getattr__(__name: str) -> Any: ...  # incomplete
