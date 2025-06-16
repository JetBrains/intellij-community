#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import sys
from typing import IO

_FD = int | IO[str]

if sys.platform != "win32":
    # XXX: Undocumented integer constants
    IFLAG: int
    OFLAG: int
    CFLAG: int
    LFLAG: int
    ISPEED: int
    OSPEED: int
    CC: int
    def setraw(fd: _FD, when: int = ...) -> None: ...
    def setcbreak(fd: _FD, when: int = ...) -> None: ...
