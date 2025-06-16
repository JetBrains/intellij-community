#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import sys

if sys.platform != "win32":
    def crypt(word: str, salt: str) -> str: ...
