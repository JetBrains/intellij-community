from __future__ import annotations

from _typeshed import StrOrBytesPath
from os import PathLike
from os.path import abspath, expanduser, expandvars
from typing import AnyStr, Union
from typing_extensions import assert_type


def test_str_path(str_path: StrOrBytesPath) -> None:
    # These methods are currently overloaded to work around python/mypy#17952 & python/mypy#11880
    # Let's ensure that they'll still work with a StrOrBytesPath if the workaround is removed

    assert_type(abspath(str_path), Union[str, bytes])
    assert_type(expanduser(str_path), Union[str, bytes])
    assert_type(expandvars(str_path), Union[str, bytes])


# See python/mypy#17952
class MyPathMissingGeneric(PathLike):  # type: ignore # Explicitly testing w/ missing type argument
    def __init__(self, path: str | bytes) -> None:
        super().__init__()
        self.path = path

    def __fspath__(self) -> str | bytes:
        return self.path


# MyPathMissingGeneric could also be fixed by users by adding the missing generic annotation
class MyPathGeneric(PathLike[AnyStr]):
    def __init__(self, path: AnyStr) -> None:
        super().__init__()
        self.path: AnyStr = path

    def __fspath__(self) -> AnyStr:
        return self.path


class MyPathStr(PathLike[str]):
    def __init__(self, path: str) -> None:
        super().__init__()
        self.path = path

    def __fspath__(self) -> str:
        return self.path


abspath(MyPathMissingGeneric("."))
expanduser(MyPathMissingGeneric("."))
expandvars(MyPathMissingGeneric("."))

abspath(MyPathGeneric("."))
expanduser(MyPathGeneric("."))
expandvars(MyPathGeneric("."))

abspath(MyPathStr("."))
expanduser(MyPathStr("."))
expandvars(MyPathStr("."))
