import typing
from typing import Optional, Union

__version__ = ...  # type: str

PyCF_ONLY_AST = ...  # type: int

identifier = str

class AST:
    _attributes = ... # type: typing.Tuple[str, ...]
    _fields = ... # type: typing.Tuple[str, ...]
    def __init__(self, *args, **kwargs) -> None: ...

class mod(AST):
    ...

class Module(mod):
    body = ...  # type: typing.List[stmt]

class Interactive(mod):
    body = ...  # type: typing.List[stmt]

class Expression(mod):
    body = ...  # type: expr

class Suite(mod):
    body = ...  # type: typing.List[stmt]


class stmt(AST):
    lineno = ...  # type: int
    col_offset = ...  # type: int

class FunctionDef(stmt):
    name = ...  # type: identifier
    args = ...  # type: arguments
    body = ...  # type: typing.List[stmt]
    decorator_list = ...  # type: typing.List[expr]

class ClassDef(stmt):
    name = ...  # type: identifier
    bases = ...  # type: typing.List[expr]
    body = ...  # type: typing.List[stmt]
    decorator_list = ...  # type: typing.List[expr]

class Return(stmt):
    value = ...  # type: Optional[expr]

class Delete(stmt):
    targets = ...  # type: typing.List[expr]

class Assign(stmt):
    targets = ...  # type: typing.List[expr]
    value = ...  # type: expr

class AugAssign(stmt):
    target = ...  # type: expr
    op = ...  # type: operator
    value = ...  # type: expr

class Print(stmt):
    dest = ...  # type: Optional[expr]
    values = ...  # type: typing.List[expr]
    nl = ...  # type: bool

class For(stmt):
    target = ...  # type: expr
    iter = ...  # type: expr
    body = ...  # type: typing.List[stmt]
    orelse = ...  # type: typing.List[stmt]

class While(stmt):
    test = ...  # type: expr
    body = ...  # type: typing.List[stmt]
    orelse = ...  # type: typing.List[stmt]

class If(stmt):
    test = ...  # type: expr
    body = ...  # type: typing.List[stmt]
    orelse = ...  # type: typing.List[stmt]

class With(stmt):
    context_expr = ...  # type: expr
    optional_vars = ...  # type: Optional[expr]
    body = ...  # type: typing.List[stmt]

class Raise(stmt):
    type = ...  # type: Optional[expr]
    inst = ...  # type: Optional[expr]
    tback = ...  # type: Optional[expr]

class TryExcept(stmt):
    body = ...  # type: typing.List[stmt]
    handlers = ...  # type: typing.List[ExceptHandler]
    orelse = ...  # type: typing.List[stmt]

class TryFinally(stmt):
    body = ...  # type: typing.List[stmt]
    finalbody = ...  # type: typing.List[stmt]

class Assert(stmt):
    test = ...  # type: expr
    msg = ...  # type: Optional[expr]

class Import(stmt):
    names = ...  # type: typing.List[alias]

class ImportFrom(stmt):
    module = ...  # type: Optional[identifier]
    names = ...  # type: typing.List[alias]
    level = ...  # type: Optional[int]

class Exec(stmt):
    body = ...  # type: expr
    globals = ...  # type: Optional[expr]
    locals = ...  # type: Optional[expr]

class Global(stmt):
    names = ...  # type: typing.List[identifier]

class Expr(stmt):
    value = ...  # type: expr

class Pass(stmt): ...
class Break(stmt): ...
class Continue(stmt): ...


class slice(AST):
    ...

_slice = slice  # this lets us type the variable named 'slice' below

class Slice(slice):
    lower = ...  # type: Optional[expr]
    upper = ...  # type: Optional[expr]
    step = ...  # type: Optional[expr]

class ExtSlice(slice):
    dims = ...  # type: typing.List[slice]

class Index(slice):
    value = ...  # type: expr

class Ellipsis(slice): ...


class expr(AST):
    lineno = ...  # type: int
    col_offset = ...  # type: int

class BoolOp(expr):
    op = ...  # type: boolop
    values = ...  # type: typing.List[expr]

class BinOp(expr):
    left = ...  # type: expr
    op = ...  # type: operator
    right = ...  # type: expr

class UnaryOp(expr):
    op = ...  # type: unaryop
    operand = ...  # type: expr

class Lambda(expr):
    args = ...  # type: arguments
    body = ...  # type: expr

class IfExp(expr):
    test = ...  # type: expr
    body = ...  # type: expr
    orelse = ...  # type: expr

class Dict(expr):
    keys = ...  # type: typing.List[expr]
    values = ...  # type: typing.List[expr]

class Set(expr):
    elts = ...  # type: typing.List[expr]

class ListComp(expr):
    elt = ...  # type: expr
    generators = ...  # type: typing.List[comprehension]

class SetComp(expr):
    elt = ...  # type: expr
    generators = ...  # type: typing.List[comprehension]

class DictComp(expr):
    key = ...  # type: expr
    value = ...  # type: expr
    generators = ...  # type: typing.List[comprehension]

class GeneratorExp(expr):
    elt = ...  # type: expr
    generators = ...  # type: typing.List[comprehension]

class Yield(expr):
    value = ...  # type: Optional[expr]

class Compare(expr):
    left = ...  # type: expr
    ops = ...  # type: typing.List[cmpop]
    comparators = ...  # type: typing.List[expr]

class Call(expr):
    func = ...  # type: expr
    args = ...  # type: typing.List[expr]
    keywords = ...  # type: typing.List[keyword]
    starargs = ...  # type: Optional[expr]
    kwargs = ...  # type: Optional[expr]

class Repr(expr):
    value = ...  # type: expr

class Num(expr):
    n = ...  # type: Union[int, float]

class Str(expr):
    s = ...  # type: str

class Attribute(expr):
    value = ...  # type: expr
    attr = ...  # type: identifier
    ctx = ...  # type: expr_context

class Subscript(expr):
    value = ...  # type: expr
    slice = ...  # type: _slice
    ctx = ...  # type: expr_context

class Name(expr):
    id = ...  # type: identifier
    ctx = ...  # type: expr_context

class List(expr):
    elts = ...  # type: typing.List[expr]
    ctx = ...  # type: expr_context

class Tuple(expr):
    elts = ...  # type: typing.List[expr]
    ctx = ...  # type: expr_context


class expr_context(AST):
    ...

class AugLoad(expr_context): ...
class AugStore(expr_context): ...
class Del(expr_context): ...
class Load(expr_context): ...
class Param(expr_context): ...
class Store(expr_context): ...


class boolop(AST):
    ...

class And(boolop): ...
class Or(boolop): ...

class operator(AST):
    ...

class Add(operator): ...
class BitAnd(operator): ...
class BitOr(operator): ...
class BitXor(operator): ...
class Div(operator): ...
class FloorDiv(operator): ...
class LShift(operator): ...
class Mod(operator): ...
class Mult(operator): ...
class Pow(operator): ...
class RShift(operator): ...
class Sub(operator): ...

class unaryop(AST):
    ...

class Invert(unaryop): ...
class Not(unaryop): ...
class UAdd(unaryop): ...
class USub(unaryop): ...

class cmpop(AST):
    ...

class Eq(cmpop): ...
class Gt(cmpop): ...
class GtE(cmpop): ...
class In(cmpop): ...
class Is(cmpop): ...
class IsNot(cmpop): ...
class Lt(cmpop): ...
class LtE(cmpop): ...
class NotEq(cmpop): ...
class NotIn(cmpop): ...


class comprehension(AST):
    target = ...  # type: expr
    iter = ...  # type: expr
    ifs = ...  # type: typing.List[expr]


class ExceptHandler(AST):
    type = ...  # type: Optional[expr]
    name = ...  # type: Optional[expr]
    body = ...  # type: typing.List[stmt]
    lineno = ...  # type: int
    col_offset = ...  # type: int


class arguments(AST):
    args = ...  # type: typing.List[expr]
    vararg = ...  # type: Optional[identifier]
    kwarg = ...  # type: Optional[identifier]
    defaults = ...  # type: typing.List[expr]

class keyword(AST):
    arg = ...  # type: identifier
    value = ...  # type: expr

class alias(AST):
    name = ...  # type: identifier
    asname = ...  # type: Optional[identifier]
