from __future__ import annotations

import io
import pathlib
import zipfile
from typing import Literal

###
# Tests for `zipfile.ZipFile`
###

p = pathlib.Path("test.zip")


class CustomPathObj:
    def __init__(self, path: str) -> None:
        self.path = path

    def __fspath__(self) -> str:
        return self.path


class NonPathObj:
    def __init__(self, path: str) -> None:
        self.path = path


class ReadableObj:
    def seek(self, offset: int, whence: int = 0) -> int:
        return 0

    def read(self, n: int | None = -1) -> bytes:
        return b"test"


class TellableObj:
    def tell(self) -> int:
        return 0


class WriteableObj:
    def close(self) -> None:
        pass

    def write(self, b: bytes) -> int:
        return len(b)

    def flush(self) -> None:
        pass


class ReadTellableObj(ReadableObj):
    def tell(self) -> int:
        return 0


class SeekTellObj:
    def seek(self, offset: int, whence: int = 0) -> int:
        return 0

    def tell(self) -> int:
        return 0


def write_zip(mode: Literal["r", "w", "x", "a"]) -> None:
    # Test any mode with `pathlib.Path`
    with zipfile.ZipFile(p, mode) as z:
        z.writestr("test.txt", "test")

    # Test any mode with `str` path
    with zipfile.ZipFile("test.zip", mode) as z:
        z.writestr("test.txt", "test")

    # Test any mode with `os.PathLike` object
    with zipfile.ZipFile(CustomPathObj("test.zip"), mode) as z:
        z.writestr("test.txt", "test")

    # Non-PathLike object should raise an error
    with zipfile.ZipFile(NonPathObj("test.zip"), mode) as z:  # type: ignore
        z.writestr("test.txt", "test")

    # IO[bytes] like-obj should work for any mode.
    io_obj = io.BytesIO(b"This is a test")
    with zipfile.ZipFile(io_obj, mode) as z:
        z.writestr("test.txt", "test")

    # Readable object should not work for any mode.
    with zipfile.ZipFile(ReadableObj(), mode) as z:  # type: ignore
        z.writestr("test.txt", "test")

    # Readable object should work for "r" mode.
    with zipfile.ZipFile(ReadableObj(), "r") as z:
        z.writestr("test.txt", "test")

    # Readable/tellable object should work for "a" mode.
    with zipfile.ZipFile(ReadTellableObj(), "a") as z:
        z.writestr("test.txt", "test")

    # If it doesn't have 'tell' method, it should raise an error.
    with zipfile.ZipFile(ReadableObj(), "a") as z:  # type: ignore
        z.writestr("test.txt", "test")

    # Readable object should not work for "w" mode.
    with zipfile.ZipFile(ReadableObj(), "w") as z:  # type: ignore
        z.writestr("test.txt", "test")

    # Tellable object should not work for any mode.
    with zipfile.ZipFile(TellableObj(), mode) as z:  # type: ignore
        z.writestr("test.txt", "test")

    # Tellable object shouldn't work for "w" mode.
    # As `__del__` will call close.
    with zipfile.ZipFile(TellableObj(), "w") as z:  # type: ignore
        z.writestr("test.txt", "test")

    # Writeable object should not work for any mode.
    with zipfile.ZipFile(WriteableObj(), mode) as z:  # type: ignore
        z.writestr("test.txt", "test")

    # Writeable object should work for "w" mode.
    with zipfile.ZipFile(WriteableObj(), "w") as z:
        z.writestr("test.txt", "test")

    # Seekable and Tellable object should not work for any mode.
    with zipfile.ZipFile(SeekTellObj(), mode) as z:  # type: ignore
        z.writestr("test.txt", "test")

    # Seekable and Tellable object shouldn't work for "w" mode.
    # Cause `__del__` will call close.
    with zipfile.ZipFile(SeekTellObj(), "w") as z:  # type: ignore
        z.writestr("test.txt", "test")
