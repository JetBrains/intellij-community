import sys
from _typeshed import Self
from typing import Any, Iterator, Sequence, Text

if sys.version_info >= (3, 0):
    from configparser import ConfigParser
else:
    from ConfigParser import ConfigParser

if sys.version_info >= (3, 8):
    from re import Pattern
else:
    from typing import Pattern

entry_point_pattern: Pattern[Text]
file_in_zip_pattern: Pattern[Text]

class BadEntryPoint(Exception):
    epstr: Text
    def __init__(self, epstr: Text) -> None: ...
    @staticmethod
    def err_to_warnings() -> Iterator[None]: ...

class NoSuchEntryPoint(Exception):
    group: Text
    name: Text
    def __init__(self, group: Text, name: Text) -> None: ...

class EntryPoint:
    name: Text
    module_name: Text
    object_name: Text
    extras: Sequence[Text] | None
    distro: Distribution | None
    def __init__(
        self,
        name: Text,
        module_name: Text,
        object_name: Text,
        extras: Sequence[Text] | None = ...,
        distro: Distribution | None = ...,
    ) -> None: ...
    def load(self) -> Any: ...
    @classmethod
    def from_string(cls: type[Self], epstr: Text, name: Text, distro: Distribution | None = ...) -> Self: ...

class Distribution:
    name: Text
    version: Text
    def __init__(self, name: Text, version: Text) -> None: ...

def iter_files_distros(
    path: Sequence[Text] | None = ..., repeated_distro: Text = ...
) -> Iterator[tuple[ConfigParser, Distribution | None]]: ...
def get_single(group: Text, name: Text, path: Sequence[Text] | None = ...) -> EntryPoint: ...
def get_group_named(group: Text, path: Sequence[Text] | None = ...) -> dict[str, EntryPoint]: ...
def get_group_all(group: Text, path: Sequence[Text] | None = ...) -> list[EntryPoint]: ...
