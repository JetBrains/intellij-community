# Stubs for sqlalchemy.sql.expression (Python 2)

from typing import Any
from . import functions
from . import elements
from . import base
from . import selectable
from . import dml

func = functions.func # type: functions._FunctionGenerator
modifier = functions.modifier # type: functions._FunctionGenerator

from .visitors import Visitable

from .elements import ClauseElement, ColumnElement,\
    BindParameter, UnaryExpression, BooleanClauseList, \
    Label, Cast, Case, ColumnClause, TextClause, Over, Null, \
    True_, False_, BinaryExpression, Tuple, TypeClause, Extract, \
    Grouping, not_, \
    collate, literal_column, between,\
    literal, outparam, type_coerce, ClauseList, FunctionFilter
from .elements import SavepointClause, RollbackToSavepointClause, \
    ReleaseSavepointClause
from .base import ColumnCollection, Generative, Executable
from .selectable import Alias, Join, Select, Selectable, TableClause, \
    CompoundSelect, CTE, FromClause, FromGrouping, SelectBase, \
    alias, GenerativeSelect, \
    subquery, HasPrefixes, HasSuffixes, Exists, ScalarSelect, TextAsFrom
from .dml import Insert, Update, Delete, UpdateBase, ValuesBase

and_ = ... # type: Any
or_ = ... # type: Any
bindparam = ... # type: Any
select = ... # type: Any
text = ... # type: Any
table = ... # type: Any
column = ... # type: Any
over = ... # type: Any
label = ... # type: Any
case = ... # type: Any
cast = ... # type: Any
extract = ... # type: Any
tuple_ = ... # type: Any
except_ = ... # type: Any
except_all = ... # type: Any
intersect = ... # type: Any
intersect_all = ... # type: Any
union = ... # type: Any
union_all = ... # type: Any
exists = ... # type: Any
nullsfirst = ... # type: Any
nullslast = ... # type: Any
asc = ... # type: Any
desc = ... # type: Any
distinct = ... # type: Any
true = ... # type: Any
false = ... # type: Any
null = ... # type: Any
join = ... # type: Any
outerjoin = ... # type: Any
insert = ... # type: Any
update = ... # type: Any
delete = ... # type: Any
funcfilter = ... # type: Any

# old names for compatibility
_Executable = Executable
_BindParamClause = BindParameter
_Label = Label
_SelectBase = SelectBase
_BinaryExpression = BinaryExpression
_Cast = Cast
_Null = Null
_False = False_
_True = True_
_TextClause = TextClause
_UnaryExpression = UnaryExpression
_Case = Case
_Tuple = Tuple
_Over = Over
_Generative = Generative
_TypeClause = TypeClause
_Extract = Extract
_Exists = Exists
_Grouping = Grouping
_FromGrouping = FromGrouping
_ScalarSelect = ScalarSelect
