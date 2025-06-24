#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any

from ...sql import expression
from ...sql.schema import ColumnCollectionConstraint

class aggregate_order_by(expression.ColumnElement[Any]):
    __visit_name__: str
    stringify_dialect: str
    inherit_cache: bool
    target: Any
    type: Any
    order_by: Any
    def __init__(self, target, *order_by) -> None: ...
    def self_group(self, against: Any | None = ...): ...
    def get_children(self, **kwargs): ...

class ExcludeConstraint(ColumnCollectionConstraint):
    __visit_name__: str
    where: Any
    inherit_cache: bool
    create_drop_stringify_dialect: str
    operators: Any
    using: Any
    ops: Any
    def __init__(self, *elements, **kw) -> None: ...

def array_agg(*arg, **kw): ...
