#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Sequence

def copy_file(
    src: str,
    dst: str,
    preserve_mode: bool = ...,
    preserve_times: bool = ...,
    update: bool = ...,
    link: str | None = ...,
    verbose: bool = ...,
    dry_run: bool = ...,
) -> tuple[str, str]: ...
def move_file(src: str, dst: str, verbose: bool = ..., dry_run: bool = ...) -> str: ...
def write_file(filename: str, contents: Sequence[str]) -> None: ...
