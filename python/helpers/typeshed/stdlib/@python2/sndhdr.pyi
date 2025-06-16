#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Text

_SndHeaders = tuple[str, int, int, int, int | str]

def what(filename: Text) -> _SndHeaders | None: ...
def whathdr(filename: Text) -> _SndHeaders | None: ...
