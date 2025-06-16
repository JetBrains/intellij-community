#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import sys
from typing import Callable, Iterable

if sys.platform != "win32":
    _Reader = Callable[[int], bytes]

    STDIN_FILENO: int
    STDOUT_FILENO: int
    STDERR_FILENO: int

    CHILD: int
    def openpty() -> tuple[int, int]: ...
    def master_open() -> tuple[int, str]: ...
    def slave_open(tty_name: str) -> int: ...
    def fork() -> tuple[int, int]: ...
    def spawn(argv: str | Iterable[str], master_read: _Reader = ..., stdin_read: _Reader = ...) -> None: ...
