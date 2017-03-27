import typing
from typing import Any, Optional, Union, Generic, Iterator

class NodeVisitor():
    def visit(self, node: AST) -> Any: ...
    def generic_visit(self, node: AST) -> None: ...

class NodeTransformer(NodeVisitor):
    def generic_visit(self, node: AST) -> None: ...

def parse(source: Union[str, bytes],
          filename: Union[str, bytes] = ...,
          mode: str = ...,
          feature_version: int = ...) -> AST: ...
def copy_location(new_node: AST, old_node: AST) -> AST: ...
def dump(node: AST, annotate_fields: bool = ..., include_attributes: bool = ...) -> str: ...
def fix_missing_locations(node: AST) -> AST: ...
def get_docstring(node: AST, clean: bool = ...) -> str: ...
def increment_lineno(node: AST, n: int = ...) -> AST: ...
def iter_child_nodes(node: AST) -> Iterator[AST]: ...
def iter_fields(node: AST) -> Iterator[typing.Tuple[str, Any]]: ...
def literal_eval(node_or_string: Union[str, AST]) -> Any: ...
def walk(node: AST) -> Iterator[AST]: ...

PyCF_ONLY_AST = ...  # type: int

# ast classes

identifier = str

class AST:
    _attributes = ...  # type: typing.Tuple[str, ...]
    _fields = ...  # type: typing.Tuple[str, ...]
    def __init__(self, *args, **kwargs) -> None: ...

class mod(AST):
    ...

class Module(mod):
    body = ...  # type: typing.List[stmt]
    type_ignores = ...  # type: typing.List[TypeIgnore]

class Interactive(mod):
    body = ...  # type: typing.List[stmt]

class Expression(mod):
    body = ...  # type: expr

class FunctionType(mod):
    argtypes = ...  # type: typing.List[expr]
    returns = ...  # type: expr

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
    returns = ...  # type: Optional[expr]
    type_comment = ...  # type: Optional[str]

class AsyncFunctionDef(stmt):
    name = ...  # type: identifier
    args = ...  # type: arguments
    body = ...  # type: typing.List[stmt]
    decorator_list = ...  # type: typing.List[expr]
    returns = ...  # type: Optional[expr]
    type_comment = ...  # type: Optional[str]

class ClassDef(stmt):
    name = ...  # type: identifier
    bases = ...  # type: typing.List[expr]
    keywords = ...  # type: typing.List[keyword]
    body = ...  # type: typing.List[stmt]
    decorator_list = ...  # type: typing.List[expr]

class Return(stmt):
    value = ...  # type: Optional[expr]

class Delete(stmt):
    targets = ...  # type: typing.List[expr]

class Assign(stmt):
    targets = ...  # type: typing.List[expr]
    value = ...  # type: expr
    type_comment = ...  # type: Optional[str]

class AugAssign(stmt):
    target = ...  # type: expr
    op = ...  # type: operator
    value = ...  # type: expr

class AnnAssign(stmt):
    target = ...  # type: expr
    annotation = ...  # type: expr
    value = ...  # type: Optional[expr]
    simple = ...  # type: int

class For(stmt):
    target = ...  # type: expr
    iter = ...  # type: expr
    body = ...  # type: typing.List[stmt]
    orelse = ...  # type: typing.List[stmt]
    type_comment = ...  # type: Optional[str]

class AsyncFor(stmt):
    target = ...  # type: expr
    iter = ...  # type: expr
    body = ...  # type: typing.List[stmt]
    orelse = ...  # type: typing.List[stmt]
    type_comment = ...  # type: Optional[str]

class While(stmt):
    test = ...  # type: expr
    body = ...  # type: typing.List[stmt]
    orelse = ...  # type: typing.List[stmt]

class If(stmt):
    test = ...  # type: expr
    body = ...  # type: typing.List[stmt]
    orelse = ...  # type: typing.List[stmt]

class With(stmt):
    items = ...  # type: typing.List[withitem]
    body = ...  # type: typing.List[stmt]
    type_comment = ...  # type: Optional[str]

class AsyncWith(stmt):
    items = ...  # type: typing.List[withitem]
    body = ...  # type: typing.List[stmt]
    type_comment = ...  # type: Optional[str]

class Raise(stmt):
    exc = ...  # type: Optional[expr]
    cause = ...  # type: Optional[expr]

class Try(stmt):
    body = ...  # type: typing.List[stmt]
    handlers = ...  # type: typing.List[ExceptHandler]
    orelse = ...  # type: typing.List[stmt]
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

class Global(stmt):
    names = ...  # type: typing.List[identifier]

class Nonlocal(stmt):
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

class Await(expr):
    value = ...  # type: expr

class Yield(expr):
    value = ...  # type: Optional[expr]

class YieldFrom(expr):
    value = ...  # type: expr

class Compare(expr):
    left = ...  # type: expr
    ops = ...  # type: typing.List[cmpop]
    comparators = ...  # type: typing.List[expr]

class Call(expr):
    func = ...  # type: expr
    args = ...  # type: typing.List[expr]
    keywords = ...  # type: typing.List[keyword]

class Num(expr):
    n = ...  # type: Union[int, float]

class Str(expr):
    s = ...  # type: str

class FormattedValue(expr):
    value = ...  # type: expr
    conversion = ...  # type: typing.Optional[int]
    format_spec = ...  # type: typing.Optional[expr]

class JoinedStr(expr):
    values = ...  # type: typing.List[expr]

class Bytes(expr):
    s = ...  # type: bytes

class NameConstant(expr):
    value = ...  # type: Any

class Ellipsis(expr): ...

class Attribute(expr):
    value = ...  # type: expr
    attr = ...  # type: identifier
    ctx = ...  # type: expr_context

class Subscript(expr):
    value = ...  # type: expr
    slice = ...  # type: _slice
    ctx = ...  # type: expr_context

class Starred(expr):
    value = ...  # type: expr
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
class MatMult(operator): ...
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
    is_async = ...  # type: int


class ExceptHandler(AST):
    type = ...  # type: Optional[expr]
    name = ...  # type: Optional[identifier]
    body = ...  # type: typing.List[stmt]
    lineno = ...  # type: int
    col_offset = ...  # type: int


class arguments(AST):
    args = ...  # type: typing.List[arg]
    vararg = ...  # type: Optional[arg]
    kwonlyargs = ...  # type: typing.List[arg]
    kw_defaults = ...  # type: typing.List[expr]
    kwarg = ...  # type: Optional[arg]
    defaults = ...  # type: typing.List[expr]

class arg(AST):
    arg = ...  # type: identifier
    annotation = ...  # type: Optional[expr]
    lineno = ...  # type: int
    col_offset = ...  # type: int
    type_comment = ...  # type: typing.Optional[str]

class keyword(AST):
    arg = ...  # type: Optional[identifier]
    value = ...  # type: expr

class alias(AST):
    name = ...  # type: identifier
    asname = ...  # type: Optional[identifier]

class withitem(AST):
    context_expr = ...  # type: expr
    optional_vars = ...  # type: Optional[expr]


class TypeIgnore(AST):
    lineno = ...  # type: int
