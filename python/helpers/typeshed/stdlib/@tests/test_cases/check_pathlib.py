from __future__ import annotations

import sys
from pathlib import Path, PureWindowsPath
from typing_extensions import assert_type


class MyCustomPath(Path): ...


if Path("asdf") == Path("asdf"):
    ...

# https://github.com/python/typeshed/issues/10661
# Provide a true positive error when comparing Path to str
# mypy should report a comparison-overlap error with --strict-equality,
# and pyright should report a reportUnnecessaryComparison error
if Path("asdf") == "asdf":  # type: ignore
    ...

# Errors on comparison here are technically false positives. However, this comparison is a little
# interesting: it can never hold true on Posix, but could hold true on Windows. We should experiment
# with more accurate __new__, such that we only get an error for such comparisons on platforms
# where they can never hold true.
if PureWindowsPath("asdf") == Path("asdf"):  # type: ignore
    ...


if sys.version_info >= (3, 13):
    pth = MyCustomPath.from_uri("file:///tmp/abc.txt")
    assert_type(pth, MyCustomPath)


if sys.version_info >= (3, 14):
    pth = MyCustomPath("asdf")
    # With text path, type should be preserved.
    assert_type(pth.move_into("asdf"), MyCustomPath)
    assert_type(pth.move("asdf"), MyCustomPath)
    assert_type(pth.copy("asdf"), MyCustomPath)
    assert_type(pth.copy_into("asdf"), MyCustomPath)

    # With an actual path type, that type should be preserved.
    assert_type(pth.move_into(Path("asdf")), Path)
    assert_type(pth.move(Path("asdf")), Path)
    assert_type(pth.copy(Path("asdf")), Path)
    assert_type(pth.copy_into(Path("asdf")), Path)
