#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any

from .elements import conv as conv

class ConventionDict:
    const: Any
    table: Any
    convention: Any
    def __init__(self, const, table, convention) -> None: ...
    def __getitem__(self, key): ...
