from collections.abc import Iterator
from typing import Any
from typing_extensions import TypeAlias

class NodeVisitor:
    def visit(self, node: AST) -> Any: ...
    def generic_visit(self, node: AST) -> None: ...

class NodeTransformer(NodeVisitor):
    def generic_visit(self, node: AST) -> None: ...

def parse(source: str | bytes, filename: str | bytes = ..., mode: str = ..., feature_version: int = ...) -> AST: ...
def copy_location(new_node: AST, old_node: AST) -> AST: ...
def dump(node: AST, annotate_fields: bool = ..., include_attributes: bool = ...) -> str: ...
def fix_missing_locations(node: AST) -> AST: ...
def get_docstring(node: AST, clean: bool = ...) -> str | None: ...
def increment_lineno(node: AST, n: int = ...) -> AST: ...
def iter_child_nodes(node: AST) -> Iterator[AST]: ...
def iter_fields(node: AST) -> Iterator[tuple[str, Any]]: ...
def literal_eval(node_or_string: str | AST) -> Any: ...
def walk(node: AST) -> Iterator[AST]: ...

PyCF_ONLY_AST: int

# ast classes

_Identifier: TypeAlias = str

class AST:
    _attributes: tuple[str, ...]
    _fields: tuple[str, ...]
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
    name: _Identifier
    args: arguments
    body: list[stmt]
    decorator_list: list[expr]
    returns: expr | None
    type_comment: str | None

class AsyncFunctionDef(stmt):
    name: _Identifier
    args: arguments
    body: list[stmt]
    decorator_list: list[expr]
    returns: expr | None
    type_comment: str | None

class ClassDef(stmt):
    name: _Identifier
    bases: list[expr]
    keywords: list[keyword]
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

class AnnAssign(stmt):
    target: expr
    annotation: expr
    value: expr | None
    simple: int

class For(stmt):
    target: expr
    iter: expr
    body: list[stmt]
    orelse: list[stmt]
    type_comment: str | None

class AsyncFor(stmt):
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
    items: list[withitem]
    body: list[stmt]
    type_comment: str | None

class AsyncWith(stmt):
    items: list[withitem]
    body: list[stmt]
    type_comment: str | None

class Raise(stmt):
    exc: expr | None
    cause: expr | None

class Try(stmt):
    body: list[stmt]
    handlers: list[ExceptHandler]
    orelse: list[stmt]
    finalbody: list[stmt]

class Assert(stmt):
    test: expr
    msg: expr | None

class Import(stmt):
    names: list[alias]

class ImportFrom(stmt):
    module: _Identifier | None
    names: list[alias]
    level: int | None

class Global(stmt):
    names: list[_Identifier]

class Nonlocal(stmt):
    names: list[_Identifier]

class Expr(stmt):
    value: expr

class Pass(stmt): ...
class Break(stmt): ...
class Continue(stmt): ...
class slice(AST): ...

_Slice: TypeAlias = slice  # this lets us type the variable named 'slice' below

class Slice(slice):
    lower: expr | None
    upper: expr | None
    step: expr | None

class ExtSlice(slice):
    dims: list[slice]

class Index(slice):
    value: expr

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

class Await(expr):
    value: expr

class Yield(expr):
    value: expr | None

class YieldFrom(expr):
    value: expr

class Compare(expr):
    left: expr
    ops: list[cmpop]
    comparators: list[expr]

class Call(expr):
    func: expr
    args: list[expr]
    keywords: list[keyword]

class Num(expr):
    n: complex

class Str(expr):
    s: str
    kind: str

class FormattedValue(expr):
    value: expr
    conversion: int | None
    format_spec: expr | None

class JoinedStr(expr):
    values: list[expr]

class Bytes(expr):
    s: bytes

class NameConstant(expr):
    value: Any

class Ellipsis(expr): ...

class Attribute(expr):
    value: expr
    attr: _Identifier
    ctx: expr_context

class Subscript(expr):
    value: expr
    slice: _Slice
    ctx: expr_context

class Starred(expr):
    value: expr
    ctx: expr_context

class Name(expr):
    id: _Identifier
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
class MatMult(operator): ...
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
    is_async: int

class ExceptHandler(AST):
    type: expr | None
    name: _Identifier | None
    body: list[stmt]
    lineno: int
    col_offset: int

class arguments(AST):
    args: list[arg]
    vararg: arg | None
    kwonlyargs: list[arg]
    kw_defaults: list[expr | None]
    kwarg: arg | None
    defaults: list[expr]

class arg(AST):
    arg: _Identifier
    annotation: expr | None
    lineno: int
    col_offset: int
    type_comment: str | None

class keyword(AST):
    arg: _Identifier | None
    value: expr

class alias(AST):
    name: _Identifier
    asname: _Identifier | None

class withitem(AST):
    context_expr: expr
    optional_vars: expr | None

class TypeIgnore(AST):
    lineno: int
