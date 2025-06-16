#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import sys

def find_library(name: str) -> str | None: ...

if sys.platform == "win32":
    def find_msvcrt() -> str | None: ...
