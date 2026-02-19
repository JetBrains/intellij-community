from __future__ import annotations

import distutils.command.sdist
from _typeshed import StrPath
from os import PathLike
from pathlib import Path

from setuptools._distutils.ccompiler import CCompiler

c = distutils.command.sdist.sdist

# Test CCompiler().compile with varied sources

compiler = CCompiler()

str_list: list[str] = ["file1.c", "file2.c"]
compiler.compile(sources=str_list)

path_list: list[Path] = [Path("file1.c"), Path("file2.c")]
compiler.compile(sources=path_list)

pathlike_list: list[PathLike[str]] = [Path("file1.c"), Path("file2.c")]
compiler.compile(sources=pathlike_list)

strpath_list: list[StrPath] = [Path("file1.c"), "file2.c"]
compiler.compile(sources=strpath_list)

# Direct literals should also work
compiler.compile(sources=["file1.c", "file2.c"])
compiler.compile(sources=[Path("file1.c"), Path("file2.c")])
compiler.compile(sources=[Path("file1.c"), "file2.c"])
