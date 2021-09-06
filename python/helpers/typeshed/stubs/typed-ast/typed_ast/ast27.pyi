import typing
from typing import Any, Iterator

class NodeVisitor:
    def visit(self, node: AST) -> Any: ...
    def generic_visit(self, node: AST) -> None: ...

class NodeTransformer(NodeVisitor):
    def generic_visit(self, node: AST) -> None: ...

def parse(source: str | bytes, filename: str | bytes = ..., mode: str = ...) -> AST: ...
def copy_location(new_node: AST, old_node: AST) -> AST: ...
def dump(node: AST, annotate_fields: bool = ..., include_attributes: bool = ...) -> str: ...
def fix_missing_locations(node: AST) -> AST: ...
def get_docstring(node: AST, clean: bool = ...) -> bytes | None: ...
def increment_lineno(node: AST, n: int = ...) -> AST: ...
def iter_child_nodes(node: AST) -> Iterator[AST]: ...
def iter_fields(node: AST) -> Iterator[typing.Tuple[str, Any]]: ...
def literal_eval(node_or_string: str | AST) -> Any: ...
def walk(node: AST) -> Iterator[AST]: ...

PyCF_ONLY_AST: int

# ast classes

identifier = str

class AST:
    _attributes: typing.Tuple[str, ...]
    _fields: typing.Tuple[str, ...]
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...

class mod(AST): ...

class Module(mod):
    body: list[stmt]
    type_ignores: list[TypeIgnore]

class Interactive(mod):
    body: list[stmt]

class Expression(mod):
    body: expr

class FunctionType(mod):
    argtypes: list[expr]
    returns: expr

class Suite(mod):
    body: list[stmt]

class stmt(AST):
    lineno: int
    col_offset: int

class FunctionDef(stmt):
    name: identifier
    args: arguments
    body: list[stmt]
    decorator_list: list[expr]
    type_comment: str | None

class ClassDef(stmt):
    name: identifier
    bases: list[expr]
    body: list[stmt]
    decorator_list: list[expr]

class Return(stmt):
    value: expr | None

class Delete(stmt):
    targets: list[expr]

class Assign(stmt):
    targets: list[expr]
    value: expr
    type_comment: str | None

class AugAssign(stmt):
    target: expr
    op: operator
    value: expr

class Print(stmt):
    dest: expr | None
    values: list[expr]
    nl: bool

class For(stmt):
    target: expr
    iter: expr
    body: list[stmt]
    orelse: list[stmt]
    type_comment: str | None

class While(stmt):
    test: expr
    body: list[stmt]
    orelse: list[stmt]

class If(stmt):
    test: expr
    body: list[stmt]
    orelse: list[stmt]

class With(stmt):
    context_expr: expr
    optional_vars: expr | None
    body: list[stmt]
    type_comment: str | None

class Raise(stmt):
    type: expr | None
    inst: expr | None
    tback: expr | None

class TryExcept(stmt):
    body: list[stmt]
    handlers: list[ExceptHandler]
    orelse: list[stmt]

class TryFinally(stmt):
    body: list[stmt]
    finalbody: list[stmt]

class Assert(stmt):
    test: expr
    msg: expr | None

class Import(stmt):
    names: list[alias]

class ImportFrom(stmt):
    module: identifier | None
    names: list[alias]
    level: int | None

class Exec(stmt):
    body: expr
    globals: expr | None
    locals: expr | None

class Global(stmt):
    names: list[identifier]

class Expr(stmt):
    value: expr

class Pass(stmt): ...
class Break(stmt): ...
class Continue(stmt): ...
class slice(AST): ...

_slice = slice  # this lets us type the variable named 'slice' below

class Slice(slice):
    lower: expr | None
    upper: expr | None
    step: expr | None

class ExtSlice(slice):
    dims: list[slice]

class Index(slice):
    value: expr

class Ellipsis(slice): ...

class expr(AST):
    lineno: int
    col_offset: int

class BoolOp(expr):
    op: boolop
    values: list[expr]

class BinOp(expr):
    left: expr
    op: operator
    right: expr

class UnaryOp(expr):
    op: unaryop
    operand: expr

class Lambda(expr):
    args: arguments
    body: expr

class IfExp(expr):
    test: expr
    body: expr
    orelse: expr

class Dict(expr):
    keys: list[expr]
    values: list[expr]

class Set(expr):
    elts: list[expr]

class ListComp(expr):
    elt: expr
    generators: list[comprehension]

class SetComp(expr):
    elt: expr
    generators: list[comprehension]

class DictComp(expr):
    key: expr
    value: expr
    generators: list[comprehension]

class GeneratorExp(expr):
    elt: expr
    generators: list[comprehension]

class Yield(expr):
    value: expr | None

class Compare(expr):
    left: expr
    ops: list[cmpop]
    comparators: list[expr]

class Call(expr):
    func: expr
    args: list[expr]
    keywords: list[keyword]
    starargs: expr | None
    kwargs: expr | None

class Repr(expr):
    value: expr

class Num(expr):
    n: int | float | complex

class Str(expr):
    s: str | bytes
    kind: str

class Attribute(expr):
    value: expr
    attr: identifier
    ctx: expr_context

class Subscript(expr):
    value: expr
    slice: _slice
    ctx: expr_context

class Name(expr):
    id: identifier
    ctx: expr_context

class List(expr):
    elts: list[expr]
    ctx: expr_context

class Tuple(expr):
    elts: list[expr]
    ctx: expr_context

class expr_context(AST): ...
class AugLoad(expr_context): ...
class AugStore(expr_context): ...
class Del(expr_context): ...
class Load(expr_context): ...
class Param(expr_context): ...
class Store(expr_context): ...
class boolop(AST): ...
class And(boolop): ...
class Or(boolop): ...
class operator(AST): ...
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
class unaryop(AST): ...
class Invert(unaryop): ...
class Not(unaryop): ...
class UAdd(unaryop): ...
class USub(unaryop): ...
class cmpop(AST): ...
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
    target: expr
    iter: expr
    ifs: list[expr]

class ExceptHandler(AST):
    type: expr | None
    name: expr | None
    body: list[stmt]
    lineno: int
    col_offset: int

class arguments(AST):
    args: list[expr]
    vararg: identifier | None
    kwarg: identifier | None
    defaults: list[expr]
    type_comments: list[str | None]

class keyword(AST):
    arg: identifier
    value: expr

class alias(AST):
    name: identifier
    asname: identifier | None

class TypeIgnore(AST):
    lineno: int
