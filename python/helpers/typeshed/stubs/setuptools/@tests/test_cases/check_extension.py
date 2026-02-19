from __future__ import annotations

from os import PathLike
from pathlib import Path

from setuptools import Extension

# Dummy extensions
ext1 = Extension(name="test1", sources=["file1.c", "file2.c"])  # plain list[str] works

path_sources: list[Path] = [Path("file1.c"), Path("file2.c")]
ext2 = Extension(name="test2", sources=path_sources)  # list of Path(s)

mixed_sources: list[str | PathLike[str]] = [Path("file1.c"), "file2.c"]  # or list[StrPath]
ext3 = Extension(name="test3", sources=mixed_sources)  # mixed types
