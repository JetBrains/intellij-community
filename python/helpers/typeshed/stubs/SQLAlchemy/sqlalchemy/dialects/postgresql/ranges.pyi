from typing import Any

import sqlalchemy.types as sqltypes

class RangeOperators:
    class comparator_factory(sqltypes.Concatenable.Comparator[Any]):
        def __ne__(self, other): ...
        def contains(self, other, **kw): ...
        def contained_by(self, other): ...
        def overlaps(self, other): ...
        def strictly_left_of(self, other): ...
        __lshift__: Any
        def strictly_right_of(self, other): ...
        __rshift__: Any
        def not_extend_right_of(self, other): ...
        def not_extend_left_of(self, other): ...
        def adjacent_to(self, other): ...
        def __add__(self, other): ...

class INT4RANGE(RangeOperators, sqltypes.TypeEngine):
    __visit_name__: str

class INT8RANGE(RangeOperators, sqltypes.TypeEngine):
    __visit_name__: str

class NUMRANGE(RangeOperators, sqltypes.TypeEngine):
    __visit_name__: str

class DATERANGE(RangeOperators, sqltypes.TypeEngine):
    __visit_name__: str

class TSRANGE(RangeOperators, sqltypes.TypeEngine):
    __visit_name__: str

class TSTZRANGE(RangeOperators, sqltypes.TypeEngine):
    __visit_name__: str
