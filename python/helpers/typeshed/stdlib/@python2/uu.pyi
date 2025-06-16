#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import BinaryIO, Text

_File = Text | BinaryIO

class Error(Exception): ...

def encode(in_file: _File, out_file: _File, name: str | None = ..., mode: int | None = ...) -> None: ...
def decode(in_file: _File, out_file: _File | None = ..., mode: int | None = ..., quiet: int = ...) -> None: ...
