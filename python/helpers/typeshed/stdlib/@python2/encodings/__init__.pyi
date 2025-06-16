#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import codecs
from typing import Any

def search_function(encoding: str) -> codecs.CodecInfo: ...

# Explicitly mark this package as incomplete.
def __getattr__(name: str) -> Any: ...
