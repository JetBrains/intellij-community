from typing import Any
from typing_extensions import TypeAlias

from ...util import symbol

_ClassDef: TypeAlias = Any  # mypy.nodes.ClassDef
_Expression: TypeAlias = Any  # mypy.nodes.Expression
_MemberExpr: TypeAlias = Any  # mypy.nodes.MemberExpr
_NameExpr: TypeAlias = Any  # mypy.nodes.NameExpr
_SymbolNode: TypeAlias = Any  # mypy.nodes.SymbolNode
_TypeInfo: TypeAlias = Any  # mypy.nodes.TypeInfo
_SemanticAnalyzerPluginInterface: TypeAlias = Any  # mypy.plugin.SemanticAnalyzerPluginInterface
_UnboundType: TypeAlias = Any  # mypy.types.UnboundType

COLUMN: symbol
RELATIONSHIP: symbol
REGISTRY: symbol
COLUMN_PROPERTY: symbol
TYPEENGINE: symbol
MAPPED: symbol
DECLARATIVE_BASE: symbol
DECLARATIVE_META: symbol
MAPPED_DECORATOR: symbol
SYNONYM_PROPERTY: symbol
COMPOSITE_PROPERTY: symbol
DECLARED_ATTR: symbol
MAPPER_PROPERTY: symbol
AS_DECLARATIVE: symbol
AS_DECLARATIVE_BASE: symbol
DECLARATIVE_MIXIN: symbol
QUERY_EXPRESSION: symbol

def has_base_type_id(info: _TypeInfo, type_id: int) -> bool: ...
def mro_has_id(mro: list[_TypeInfo], type_id: int) -> bool: ...
def type_id_for_unbound_type(type_: _UnboundType, cls: _ClassDef, api: _SemanticAnalyzerPluginInterface) -> int | None: ...
def type_id_for_callee(callee: _Expression) -> int | None: ...
def type_id_for_named_node(node: _NameExpr | _MemberExpr | _SymbolNode) -> int | None: ...
def type_id_for_fullname(fullname: str) -> int | None: ...
