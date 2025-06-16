#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from itertools import ifilter, imap, izip
from typing import Any

filter = ifilter
map = imap
zip = izip

def ascii(obj: Any) -> str: ...
def hex(x: int) -> str: ...
def oct(x: int) -> str: ...
