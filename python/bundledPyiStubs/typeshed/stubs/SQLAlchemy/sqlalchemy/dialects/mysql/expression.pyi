#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any

from ...sql import elements
from ...sql.base import Generative

class match(Generative, elements.BinaryExpression):
    __visit_name__: str
    inherit_cache: bool
    def __init__(self, *cols, **kw) -> None: ...
    modifiers: Any
    def in_boolean_mode(self) -> None: ...
    def in_natural_language_mode(self) -> None: ...
    def with_query_expansion(self) -> None: ...
